// Front de la API de tareas. Consume las operaciones de docs/contratos/api.md.
// Seguridad: los datos del servidor se pintan solo con textContent (nunca innerHTML).
// La sesión (HTTP Basic) vive solo en memoria: se pierde al recargar y nunca va a localStorage.
"use strict";

const BASE = window.TODOS_CONFIG.apiUrl;
const API = `${BASE}/api/todos`;

const $lista = document.getElementById("lista");
const $vacio = document.getElementById("vacio");
const $estado = document.getElementById("estado");
const $formCrear = document.getElementById("form-crear");
const $tpl = document.getElementById("tpl-tarea");
const fechaFormato = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium", timeStyle: "short" });

const campo = (form, nombre) => form.elements.namedItem(nombre);

let filtro = ""; // "", "true" o "false"
let credenciales = null; // "usuario:contraseña" en Base64, solo en memoria

// ---------- Llamadas a la API ----------

const base64 = (texto) => btoa(String.fromCharCode(...new TextEncoder().encode(texto)));

/** Las lecturas son públicas; las escrituras (auth: true) envían Authorization. */
async function api(url, options = {}, { auth = false } = {}) {
  const headers = {};
  if (options.body) headers["Content-Type"] = "application/json";
  if (auth && credenciales) headers.Authorization = `Basic ${credenciales}`;

  let res;
  try {
    res = await fetch(url, { ...options, headers });
  } catch {
    throw new Error("No se pudo conectar con el servidor. Intenta de nuevo en unos segundos.");
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401) cerrarSesion();
    // El backend siempre responde { status, error, message, timestamp }
    throw new Error(data?.message ?? "Error inesperado");
  }
  return data;
}

const escribir = (path, options) => api(API + path, options, { auth: true });

const listar = () => api(API + (filtro === "" ? "" : `?completed=${filtro}`));
const crear = (tarea) => escribir("", { method: "POST", body: JSON.stringify(tarea) });
const editar = (id, tarea) => escribir(`/${id}`, { method: "PUT", body: JSON.stringify(tarea) });
const alternar = (id) => escribir(`/${id}/toggle`, { method: "PATCH" });
const eliminar = (id) => escribir(`/${id}`, { method: "DELETE" });
const quienSoy = () => api(`${BASE}/api/auth/me`, {}, { auth: true });

// ---------- Sesión ----------

const $formLogin = document.getElementById("form-login");
const $sesionActiva = document.getElementById("sesion-activa");

function actualizarSesion(usuario = null) {
  const activa = Boolean(credenciales && usuario);
  if (activa) document.body.dataset.sesion = "si";
  else delete document.body.dataset.sesion;
  $formLogin.hidden = activa;
  $sesionActiva.hidden = !activa;
  document.getElementById("usuario-actual").textContent = activa ? usuario : "";
  $lista.querySelectorAll(".todo__check").forEach((c) => { c.disabled = !activa; });
}

function cerrarSesion() {
  credenciales = null;
  actualizarSesion();
}

// ---------- Interfaz ----------

function mostrarEstado(texto, tipo = "info") {
  $estado.textContent = texto;
  $estado.dataset.tipo = tipo;
}

function pintarTarea(tarea) {
  const li = $tpl.content.firstElementChild.cloneNode(true);
  li.dataset.id = tarea.id;
  li.classList.toggle("is-done", tarea.completed);

  const check = li.querySelector(".todo__check");
  check.checked = tarea.completed;
  check.disabled = !credenciales;
  li.querySelector(".todo__title").textContent = tarea.title;
  const desc = li.querySelector(".todo__description");
  desc.textContent = tarea.description ?? "";
  desc.hidden = !tarea.description;
  li.querySelector(".todo__date").textContent = `Creada: ${fechaFormato.format(new Date(tarea.createdAt))}`;

  const vista = li.querySelector(".todo__view");
  const form = li.querySelector(".todo__edit");

  check.addEventListener("change", () =>
    ejecutar(() => alternar(tarea.id), tarea.completed ? "Tarea marcada como pendiente" : "Tarea completada"));

  li.querySelector('[data-action="eliminar"]').addEventListener("click", () => {
    if (confirm(`¿Eliminar "${tarea.title}"?`)) ejecutar(() => eliminar(tarea.id), "Tarea eliminada");
  });

  li.querySelector('[data-action="editar"]').addEventListener("click", () => {
    campo(form, "title").value = tarea.title;
    campo(form, "description").value = tarea.description ?? "";
    campo(form, "completed").checked = tarea.completed;
    vista.hidden = true;
    form.hidden = false;
    campo(form, "title").focus();
  });

  li.querySelector('[data-action="cancelar"]').addEventListener("click", () => {
    form.hidden = true;
    vista.hidden = false;
  });

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    ejecutar(() => editar(tarea.id, {
      title: campo(form, "title").value,
      description: campo(form, "description").value,
      completed: campo(form, "completed").checked,
    }), "Tarea actualizada");
  });

  return li;
}

async function cargar() {
  mostrarEstado("Cargando…");
  try {
    const tareas = await listar();
    $lista.replaceChildren(...tareas.map(pintarTarea));
    $vacio.hidden = tareas.length > 0;
    mostrarEstado("");
  } catch (e) {
    mostrarEstado(e.message, "error");
  }
}

/** Ejecuta una operación, muestra el resultado y recarga la lista. */
async function ejecutar(operacion, mensajeOk) {
  try {
    await operacion();
    await cargar();
    mostrarEstado(mensajeOk, "ok");
    return true;
  } catch (e) {
    mostrarEstado(e.message, "error");
    return false;
  }
}

// ---------- Eventos ----------

$formLogin.addEventListener("submit", async (e) => {
  e.preventDefault();
  const usuario = campo($formLogin, "usuario").value.trim();
  const password = campo($formLogin, "password").value;
  if (!usuario || !password) {
    mostrarEstado("Escribe tu usuario y tu contraseña", "error");
    return;
  }
  credenciales = base64(`${usuario}:${password}`);
  try {
    const sesion = await quienSoy();
    actualizarSesion(sesion.username);
    $formLogin.reset();
    mostrarEstado(`Hola, ${sesion.username}`, "ok");
  } catch (err) {
    cerrarSesion();
    mostrarEstado(err.message, "error");
  }
});

document.getElementById("btn-salir").addEventListener("click", () => {
  cerrarSesion();
  mostrarEstado("Sesión cerrada", "ok");
});

$formCrear.addEventListener("submit", async (e) => {
  e.preventDefault();
  const ok = await ejecutar(() => crear({
    title: campo($formCrear, "title").value,
    description: campo($formCrear, "description").value,
  }), "Tarea creada");
  if (ok) $formCrear.reset();
});

document.querySelectorAll(".filter").forEach((boton) => {
  boton.addEventListener("click", () => {
    document.querySelectorAll(".filter").forEach((b) => b.classList.toggle("is-active", b === boton));
    filtro = boton.dataset.filter;
    cargar();
  });
});

cargar();
