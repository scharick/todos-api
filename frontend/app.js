// Front de la API de tareas. Consume las operaciones de docs/contratos/api.md.
// Seguridad: los datos del servidor se pintan solo con textContent (nunca innerHTML).
"use strict";

const API = `${window.TODOS_CONFIG.apiUrl}/api/todos`;

const $lista = document.getElementById("lista");
const $vacio = document.getElementById("vacio");
const $estado = document.getElementById("estado");
const $formCrear = document.getElementById("form-crear");
const $tpl = document.getElementById("tpl-tarea");
const fechaFormato = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium", timeStyle: "short" });

const campo = (form, nombre) => form.elements.namedItem(nombre);

let filtro = ""; // "", "true" o "false"

// ---------- Llamadas a la API ----------

async function api(path = "", options = {}) {
  let res;
  try {
    res = await fetch(API + path, {
      ...options,
      headers: options.body ? { "Content-Type": "application/json" } : undefined,
    });
  } catch {
    throw new Error("No se pudo conectar con el servidor. Intenta de nuevo en unos segundos.");
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    // El backend siempre responde { status, error, message, timestamp }
    throw new Error(data?.message ?? "Error inesperado");
  }
  return data;
}

const listar = () => api(filtro === "" ? "" : `?completed=${filtro}`);
const crear = (tarea) => api("", { method: "POST", body: JSON.stringify(tarea) });
const editar = (id, tarea) => api(`/${id}`, { method: "PUT", body: JSON.stringify(tarea) });
const alternar = (id) => api(`/${id}/toggle`, { method: "PATCH" });
const eliminar = (id) => api(`/${id}`, { method: "DELETE" });

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
