// URL del backend. En local usa Spring Boot en el puerto 8080;
// publicado en Vercel usa el backend de Render.
window.TODOS_CONFIG = {
  apiUrl: ["localhost", "127.0.0.1"].includes(location.hostname)
    ? "http://localhost:8080"
    : "https://todos-api-hoal.onrender.com",
};
