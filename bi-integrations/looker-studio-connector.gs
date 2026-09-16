/***************************************************************
 * Aircargo BI Connector para Google Looker Studio
 * ─────────────────────────────────────────────────────────────
 * Conector comunitario (Community Connector) en Apps Script que
 * consume los endpoints de `/api/bi/**` expuestos por el gateway
 * de Aircargo usando el API Key (service token) del usuario BI.
 *
 * INSTALACIÓN:
 *   1. script.google.com → Nuevo proyecto → pegar este archivo.
 *   2. Implementación → Nuevo despliegue → Tipo "Add-on / Editor":
 *      appsscript.json → "urlFetchWhitelist" NO es necesario.
 *      Marcar "Looker Studio" en la configuración del despliegue
 *      (Referencias del editor → Cloud Projects habilitado).
 *   3. En Looker Studio: Crear fuente de datos → Conectores
 *      (Partners) → tomar el ID del despliegue "Looker Studio Business Share".
 *
 *   El host DEBE ser HTTPS y alcanzable desde Internet (Apps Script
 *   corre en la nube de Google). Para un servidor privado: usar un
 *   túnel/tunnel HTTPS (p.ej. nginx + Let's Encrypt, Cloudflare Tunnel,
 *   ngrok) delante del gateway.
 *
 *   NOTA DE SEGURIDAD: si el deployment es público, el API Key viaja
 *   en la URL de cada petición (Looker Studio lo guarda cifrado en su
 *   catálogo). Regenera el token en Settings → BI y rota por aerolínea
 *   si lo revocas.
 ***************************************************************/

var ENDPOINTS = [
  { id: 'bi/flights',          label: 'Vuelos / Flights' },
  { id: 'bi/bookings',         label: 'Reservas / Bookings' },
  { id: 'bi/mawbs',            label: 'MAWBs' },
  { id: 'bi/receipts',         label: 'Recibos / Receipts' },
  { id: 'bi/ulds',             label: 'ULDs' },
  { id: 'bi/daily',            label: 'Diario / Daily' },
  { id: 'bi/by-location',      label: 'Por ubicación / By Location' },
  { id: 'bi/timeline',         label: 'Línea de tiempo / Timeline' },
  { id: 'bi/top-mawbs',        label: 'Top MAWBs' },
  { id: 'bi/flight-performance', label: 'Desempeño de vuelos' },
  { id: 'bi/dashboard',        label: 'Dashboard (KPIs)' },
  { id: 'bi/summary',          label: 'Resumen / Summary' },
  { id: 'bi/weight-report',    label: 'Reporte de peso / Weight Report' },
  { id: 'bi/weight-summary',   label: 'Resumen de peso / Weight Summary' }
];

/**
 * Requerido por Looker Studio. Devuelve true para autorizar el acceso
 * al editor (el API Key se configura en la fuente de datos, no aquí).
 */
function isAdminUser() {
  return true;
}

/**
 * Autenticación NONE: el token se envía como ?api_key= en cada request.
 */
function getAuthType() {
  return { type: 'NONE' };
}

/**
 * Parámetros configurables en la fuente de datos de Looker Studio.
 */
function getConfig(request) {
  var cc = DataStudioApp.createCommunityConnector();
  var config = cc.newConfig()
    .setDateRangeRequired(false)
    .setIsSteppedConfig(false);

  config.newTextInput()
    .setId('baseUrl')
    .setName('URL base del gateway de Aircargo')
    .setHelpText('Ej.: https://tu-dominio.com  (HTTPS obligatorio; sin barra final)')
    .setPlaceholder('https://tu-dominio.com')
    .setAllowOverride(true);

  config.newTextInput()
    .setId('apiKey')
    .setName('API Key (service token de BI)')
    .setHelpText('Token que se pega tras el botón "Servicio BI" en Configuración → BI. Se envía como ?api_key= en cada petición.')
    .setPlaceholder('eyJ...')
    .setAllowOverride(true);

  config.newSelectSingle()
    .setId('endpoint')
    .setName('Endpoint de datos')
    .setHelpText('El /api/ no hace falta: elige el recurso.')
    .setAllowOverride(true);

  for (var i = 0; i < ENDPOINTS.length; i++) {
    config.newOptionBuilder()
      .setLabel(ENDPOINTS[i].label)
      .setValue(ENDPOINTS[i].id)
      .build();
  }

  config.newTextInput()
    .setId('maxRows')
    .setName('Filas máximas por carga')
    .setPlaceholder('1000')
    .setAllowOverride(true);

  return config.build();
}

/* ---------------- helpers ---------------- */

function getFieldType(value) {
  return (typeof value === 'number' && isFinite(value)) ? 'NUMBER' : 'TEXT';
}

function getBaseUrl(cfg) {
  return String(cfg.baseUrl || '').replace(/\/+$/, '');
}

function getMaxRows(cfg) {
  var n = parseInt(cfg.maxRows, 10);
  return (!isNaN(n) && n > 0) ? Math.min(n, 5000) : 1000;
}

/**
 * Lanza un error legible para el usuario de Looker Studio.
 */
function userError(message) {
  var cc = DataStudioApp.createCommunityConnector();
  throw cc.newUserError().setDebugText(message);
}

/**
 * Llama al endpoint con ?api_key= y devuelve el JSON (Array o Map).
 */
function fetchJson(cfg) {
  var base = getBaseUrl(cfg);
  if (!base) userError('Falta la URL base del gateway (Configuración).');
  if (!cfg.apiKey) userError('Falta el API Key de BI (Configuración).');

  var url = base + '/api/' + cfg.endpoint + '?api_key=' + encodeURIComponent(cfg.apiKey);
  if (cfg.dateRange && cfg.dateRange.startDate) {
    url += '&dateFrom=' + encodeURIComponent(cfg.dateRange.startDate);
  }
  if (cfg.dateRange && cfg.dateRange.endDate) {
    url += '&dateTo=' + encodeURIComponent(cfg.dateRange.endDate);
  }

  var resp = UrlFetchApp.fetch(url, {
    muteHttpExceptions: true,
    headers: { 'Accept': 'application/json' }
  });

  if (resp.getResponseCode() >= 400) {
    userError('El gateway respondió ' + resp.getResponseCode() + ' para ' + cfg.endpoint +
      '. Revisa el API Key (Settings → BI → Servicio BI) y que la ruta exista.');
  }
  try {
    return JSON.parse(resp.getContentText());
  } catch (e) {
    userError('No se pudo leer JSON del endpoint ' + cfg.endpoint + '. ¿El host responde?');
  }
}

/**
 * Normaliza la respuesta del backend (Lista de Map o Map único) → [ {fila} ], y
 * deriva las columnas de la PRIMERA fila no vacía.
 */
function normalize(payload) {
  if (payload === null || payload === undefined) return [];
  if (Array.isArray(payload)) return payload.filter(function (r) { return r && typeof r === 'object'; });
  return [payload]; // Map único: se aplana como una sola fila
}

function sanitizeKey(name) {
  return String(name).replace(/[^A-Za-z0-9_]/g, '_');
}

/**
 * Construye el schema (campos) a partir de una fila de muestra.
 */
function buildFields(sampleRow) {
  var fields = [];
  var keys = Object.keys(sampleRow || {});
  for (var i = 0; i < keys.length; i++) {
    fields.push({
      name: sanitizeKey(keys[i]),
      label: keys[i],
      dataType: getFieldType(sampleRow[keys[i]])
    });
  }
  return fields;
}

/* ---------------- API de Looker Studio ---------------- */

/**
 * Devuelve el esquema de columnas derivado de una muestra real del
 * endpoint seleccionado (1 fila). Así el editor nunca se queda sin
 * campos al añadir el conector.
 */
function getSchema(request) {
  var cfg = request.configParams || {};
  var maxRows = getMaxRows(cfg);

  // Muestra acotada a 1 fila para descubrir las columnas.
  var sampleCfg = Object.assign({}, cfg, { maxRows: 1 });
  var payload = fetchJson(sampleCfg);
  var rows = normalize(payload);
  if (!rows.length) {
    userError('El endpoint «' + cfg.endpoint + '» no devolvió datos con los que derivar columnas.');
  }

  return { schema: buildFields(rows[0]) };
}

/**
 * Devuelve los datos en forma de matriz de valores.
 */
function getData(request) {
  var cfg = request.configParams || {};
  var maxRows = getMaxRows(cfg);

  var payload = fetchJson(cfg);
  var data = normalize(payload).slice(0, maxRows);

  var schema = buildFields(data.length ? data[0] : {});
  var fields = request.fields || [];
  var idx = [];
  for (var i = 0; i < fields.length; i++) {
    var key = String(fields[i].name);
    var found = -1;
    for (var j = 0; j < schema.length; j++) {
      if (schema[j].name === key) { found = j; break; }
    }
    idx.push(found);
  }

  var outRows = [];
  for (var r = 0; r < data.length; r++) {
    var src = data[r];
    var keys = Object.keys(src);
    var row = [];
    for (var c = 0; c < idx.length; c++) {
      var col = idx[c];
      if (col === -1 || col >= keys.length) { row.push(null); continue; }
      var val = src[keys[col]];
      row.push(val === null || val === undefined ? null : String(val));
    }
    outRows.push({ values: row });
  }

  // Filtra del schema solo los campos pedidos, preservando el orden de la petición.
  var outFields = [];
  for (var k = 0; k < idx.length; k++) {
    var globalIdx = idx[k];
    if (globalIdx >= 0 && globalIdx < schema.length) outFields.push(schema[globalIdx]);
  }

  return { schema: outFields, rows: outRows };
}