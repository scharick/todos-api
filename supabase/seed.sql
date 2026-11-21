-- Datos semilla (modelo.md, "Datos de ejemplo")

insert into public.todos (title, description, completed, created_at) values
  ('Estudiar Spring Boot', 'Repasar controladores y JPA', false, now() - interval '2 days'),
  ('Hacer el laboratorio', 'Modelo de datos en Mermaid', true, now() - interval '1 day'),
  ('Desplegar en Render', null, false, now());
