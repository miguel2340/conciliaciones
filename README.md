# Pagos y Radicación

Proyecto base con front-end en Angular y back-end en Spring Boot enfocado en el flujo de autenticación y consulta de radicaciones.

## Estructura

- frontend/: aplicación Angular con login, shell lateral y módulo para buscar radicaciones por NIT.
- backend/: API Spring Boot con autenticación y endpoints para leer la tabla radicacion_filtrada desde SQL Server.

## Prerrequisitos

- Node.js 18+
- Angular CLI (npm install -g @angular/cli) una vez tengas acceso a internet.
- Maven 3.9+.
- JDK 17.
- SQL Server disponible con la base fomagf accesible desde la máquina local.

## Configuración del back-end

1. Actualiza las credenciales en backend/src/main/resources/application.properties:

       spring.datasource.username=SQLSERVER_USERNAME
       spring.datasource.password=SQLSERVER_PASSWORD

   Ajusta también la URL si tu instancia no corre en localhost:1433 o si requiere parámetros distintos.

2. El esquema se crea automáticamente en las tablas users y user_roles. Al primer arranque se insertan usuarios de ejemplo con roles básicos.

## Endpoints disponibles

- POST /api/v1/auth/login: autenticación básica con usuarios almacenados en SQL Server. Devuelve un token tipo bearer válido por 1 hora.
- GET /api/v1/radicacion?nit=XXXX&page=0&size=100: devuelve resultados paginados (100 filas por defecto, hasta 500) de la vista o tabla radicacion_filtrada filtrada por NIT. Requiere enviar `Authorization: Bearer <token>`.
- GET /api/v1/radicacion/export?nit=XXXX: genera un archivo TXT delimitado por el carácter | con la misma información, listo para descarga. Requiere el mismo header de autorización.
- POST /api/v1/radicacion/export/multiple: recibe un cuerpo JSON `{ "nits": ["nit1", "nit2"] }` y devuelve un único TXT que agrega los registros de cada NIT (separados con un encabezado `# NIT`).

## Puesta en marcha

1. Instalar dependencias Angular

       cd frontend
       npm install

2. Levantar front-end

       npm start

3. Levantar back-end

       cd backend
       mvn spring-boot:run

### Flujo inicial

1. Inicia sesión con una de las cuentas sembradas automáticamente:
   - admin@empresa.com / admin123
   - operador@empresa.com / operador123
2. Tras autenticarse se mostrará el shell con menú lateral. Selecciona "Buscar reporte por NIT", ingresa un NIT y obtén la tabla paginada.
3. Usa el botón "Descargar TXT del NIT" para obtener el archivo delimitado por |.

## Próximos pasos sugeridos

- Reemplazar los tokens aleatorios por JWT con expiración y refresh real.
- Añadir guardias de ruta en Angular (canActivate) que validen AuthService.isAuthenticated.
- Implementar flujos de registro/gestión de usuarios y políticas de contraseña.
- Migrar a migraciones controladas (Flyway/Liquibase) en lugar de ddl-auto=update.
- Expandir los demás módulos del menú lateral con las consultas o cargas requeridas.
