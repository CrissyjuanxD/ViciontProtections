# ViciontProtections 3.0.6

Los clanes pueden elegir cualquier color HEX desde su diálogo y guardarlo sin pedir que se añada a la
paleta del servidor. La integración acompañante de QuasoPlugin calcula el mantenimiento por miembro:
**2 DinoCoins × 10 miembros = 20 DinoCoins al día**, cobradas del banco común.

## Cambios

- Campo **Color personalizado (#RRGGBB)** al fundar y en **Ajustes → Cambiar color**. Se mantienen los
  colores preestablecidos. Los botones indican si se aplica la lista o el HEX escrito.
- Comandos compatibles: `/clan color #B75FFF`, `/clan fundar #B75FFF Nombre del clan` y
  `/clan admin NombreDelClan color #B75FFF`. Los permisos de los rangos se mantienen.
- El color se guarda en SQL y en las copias de clanes disueltos. Se rechazan códigos inválidos con un
  mensaje que explica el formato.
- `/clan guia` incorpora **Color del clan**, también con un `clans.yml` anterior y sin sobrescribirlo.
- **QuasoPlugin #174**: cuota diaria por todos los miembros actuales, incluidos líder, invitados y
  desconectados. Las invitaciones pendientes no cuentan. No se cobra por unirse ni directamente a cada
  jugador; los miembros aportan al banco según los permisos de su rango.
- El banco, los avisos de días cubiertos y su tema en `/clan guia` muestran la nueva cuota. Se conservan
  el primer día de gracia, la hora de cobro y los plazos de impago. No cambia el esquema SQL.

## Descargas

- [ViciontProtections-3.0.6.jar](https://github.com/CrissyjuanxD/ViciontProtections/releases/download/v3.0.6/ViciontProtections-3.0.6.jar), compilado y probado por GitHub Actions desde el tag de la release.
- [QuasoPlugin-1.1.5.jar · actualización #174](https://github.com/CrissyjuanxD/QuasoPlugin/raw/a1d8e79/descargas/integracion-clanes-3.0.6/QuasoPlugin-1.1.5.jar).
- [SHA-256 de QuasoPlugin](https://github.com/CrissyjuanxD/QuasoPlugin/blob/a1d8e79/descargas/integracion-clanes-3.0.6/SHA256SUMS.txt).

## Instalar en el servidor de Quaso

1. Detén el servidor y guarda una copia de los JAR, configuraciones y bases de datos actuales.
2. Sustituye el JAR anterior de ViciontProtections por **ViciontProtections-3.0.6.jar**.
3. Sustituye también **QuasoPlugin-1.1.5.jar** por el enlazado en esta release: contiene la actualización
   **#174** de la rama `26.2`. No dejes dos JAR del mismo plugin. Este archivo acompañante requiere
   **Paper 26.2 y Java 25**; ViciontProtections conserva su compatibilidad propia y bytecode Java 21.
4. Conserva tus archivos. En `plugins/QuasoPlugin/config.yml`, la clave existente pasa a significar:

   ```yaml
   clanes:
     mantenimiento-diario: 2 # DinoCoins por miembro y día
   ```

   Si ya tienes la sección `clanes`, modifica su valor allí. El valor que tenías se conserva, pero ahora
   se multiplica por los miembros: revisa los fondos del banco antes del siguiente cobro.
5. Inicia el servidor. Comprueba `/clan ajustes`, `/clanbanco` y `/clan guia`.

**Para recibir las dos mejoras debes actualizar ambos plugins.** ViciontProtections no cobra DinoCoins;
ese cambio reside en QuasoPlugin. Sin el banco conectado, los clanes siguen funcionando sin mantenimiento.
No hace falta borrar `clans.yml`, clanes ni saldos.

## Validación

- ViciontProtections: `clean verify`, **34 pruebas sin fallos**; incluye HEX, diálogos, persistencia y guía.
- QuasoPlugin: **8 pruebas del banco sin fallos** y JAR compilado; cubren cobro único, miembros actuales,
  deuda, avisos y aportes asíncronos.
- Ambos JAR comprobados; la descarga de QuasoPlugin incluye su `SHA256SUMS.txt`.
- Revisión visual y sesión multijugador de 3.0.6 pendientes; no se han desplegado cambios en el servidor.
