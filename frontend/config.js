// URL del backend. En local usa Spring Boot en el puerto 8080;
// publicado en Vercel usa el backend de Render (cambiar cuando exista la URL).
window.TODOS_CONFIG = {
  apiUrl: ["localhost", "127.0.0.1"].includes(location.hostname)
    ? "http://localhost:8080"
    : "https://REEMPLAZAR-CON-TU-APP.onrender.com",
};
