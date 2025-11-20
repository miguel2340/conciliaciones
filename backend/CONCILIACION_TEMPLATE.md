## Plantilla Excel para Conciliación (Hoja 2)

- La exportación XLSX del módulo de conciliación admite usar una plantilla para la hoja 2 (Estado_Cuenta).
- La exportación siempre crea programáticamente la hoja `Detalle_Radicacion` (hoja 1, detallado).
- Si la plantilla ya contiene la hoja `Estado_Cuenta`, se respeta tal cual; si no existe, el backend la construye automáticamente (comportamiento actual).

### Cómo proveer la plantilla

Opción 1: variable de entorno

- Defina `CONCILIACION_XLSX_TEMPLATE` con la ruta absoluta del archivo `.xlsx` de plantilla.

Opción 2: recurso en classpath

- Coloque el archivo en `backend/src/main/resources/conciliacion/estado_cuenta_template.xlsx`.
- Alternativamente `backend/src/main/resources/estado_cuenta_template.xlsx`.

### Requisitos del template

- Las fórmulas o celdas que referencian el detalle deben apuntar a la hoja `Detalle_Radicacion`.
- Puede incluir logos, estilos y textos personalizados. Las fórmulas se recalculan al abrir el archivo.

