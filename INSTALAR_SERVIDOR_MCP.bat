@echo off
REM Instalador del servidor MCP (Model Context Protocol)
REM Este script configura un servidor Node.js que convierte cualquier archivo a JSON
REM para proporcionar contexto al modelo de IA Granite.

echo ===============================================================
echo                   INSTALADOR SERVIDOR MCP
echo                Model Context Protocol Server
echo ===============================================================
echo.
echo Este script instalara un servidor MCP que permite convertir cualquier
echo archivo (PDF, PowerPoint, Word, etc.) a formato JSON para proporcionar
echo contexto al modelo de IA Granite.
echo.

REM Verificar si Node.js está instalado
where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Node.js no esta instalado
    echo Por favor, instala Node.js desde https://nodejs.org/
    pause
    exit /b 1
)

REM Verificar versión de Node.js
for /f "tokens=*" %%a in ('node -v') do set NODE_VERSION=%%a
echo [OK] Node.js detectado: %NODE_VERSION%

REM Crear directorio para el servidor MCP
echo [INFO] Creando directorio para el servidor MCP...
if not exist mcp-server mkdir mcp-server
cd mcp-server

REM Crear package.json
echo [INFO] Creando package.json...
echo {^
  "name": "mcp-server",^
  "version": "1.0.0",^
  "description": "Model Context Protocol Server para convertir archivos a JSON",^
  "main": "server.js",^
  "scripts": {^
    "start": "node server.js"^
  },^
  "dependencies": {^
    "express": "^4.18.2",^
    "multer": "^1.4.5-lts.1",^
    "pdf-parse": "^1.1.1",^
    "mammoth": "^1.6.0",^
    "xlsx": "^0.18.5",^
    "pptx-to-json": "^1.0.0-rc.1",^
    "mime-types": "^2.1.35",^
    "cors": "^2.8.5"^
  }^
} > package.json

REM Crear server.js
echo [INFO] Creando server.js...
echo const express = require('express');^
const multer = require('multer');^
const fs = require('fs');^
const path = require('path');^
const mime = require('mime-types');^
const cors = require('cors');^
const pdfParse = require('pdf-parse');^
const mammoth = require('mammoth');^
const xlsx = require('xlsx');^
^
const app = express();^
const port = process.env.PORT ^|^| 3000;^
^
// Habilitar CORS^
app.use(cors());^
^
// Configurar almacenamiento temporal de archivos^
const storage = multer.diskStorage({^
  destination: (req, file, cb) => {^
    const tempDir = path.join(__dirname, 'temp');^
    if (!fs.existsSync(tempDir)) {^
      fs.mkdirSync(tempDir);^
    }^
    cb(null, tempDir);^
  },^
  filename: (req, file, cb) => {^
    cb(null, `${Date.now()}-${file.originalname}`);^
  }^
});^
^
const upload = multer({ storage });^
^
// Endpoint para convertir archivos^
app.post('/convert', upload.single('file'), async (req, res) => {^
  try {^
    if (!req.file) {^
      return res.status(400).json({^
        error: 'No se ha proporcionado ningun archivo'^
      });^
    }^
^
    const filePath = req.file.path;^
    const mimeType = mime.lookup(filePath) ^|^| 'application/octet-stream';^
    const fileName = req.file.originalname;^
    const fileSize = req.file.size;^
^
    console.log(`Procesando archivo: ${fileName} (${mimeType}, ${fileSize} bytes)`);^
^
    let fileContent = '';^
    let result = {^
      fileName,^
      fileType: mimeType,^
      fileSize,^
      processingTimestamp: new Date().toISOString(),^
      content: ''^
    };^
^
    // Procesar archivo segun su tipo MIME^
    try {^
      if (mimeType === 'application/pdf') {^
        // PDF^
        const dataBuffer = fs.readFileSync(filePath);^
        const pdfData = await pdfParse(dataBuffer);^
        fileContent = pdfData.text;^
        result.pageCount = pdfData.numpages;^
        result.metadata = pdfData.info;^
      } else if (mimeType.includes('word') ^|^| mimeType.includes('docx') ^|^| mimeType.includes('doc')) {^
        // Word^
        const dataBuffer = fs.readFileSync(filePath);^
        const docData = await mammoth.extractRawText({ buffer: dataBuffer });^
        fileContent = docData.value;^
      } else if (mimeType.includes('spreadsheet') ^|^| mimeType.includes('excel') ^|^| mimeType.includes('xlsx') ^|^| mimeType.includes('xls')) {^
        // Excel^
        const workbook = xlsx.readFile(filePath);^
        const sheetNames = workbook.SheetNames;^
        let sheets = {};^
        ^
        sheetNames.forEach(sheetName => {^
          const worksheet = workbook.Sheets[sheetName];^
          sheets[sheetName] = xlsx.utils.sheet_to_json(worksheet);^
        });^
        ^
        result.sheets = sheets;^
        fileContent = JSON.stringify(sheets, null, 2);^
      } else if (mimeType.includes('text') ^|^| mimeType.includes('json') ^|^| mimeType.includes('xml') ^|^| mimeType.includes('javascript') ^|^| mimeType.includes('html') ^|^| mimeType.includes('css')) {^
        // Archivos de texto (txt, json, xml, js, html, css)^
        fileContent = fs.readFileSync(filePath, 'utf8');^
      } else if (mimeType.includes('image')) {^
        // Imagenes^
        fileContent = `[Archivo de imagen: ${fileName}]`;^
        result.isImage = true;^
      } else if (mimeType.includes('video')) {^
        // Videos^
        fileContent = `[Archivo de video: ${fileName}]`;^
        result.isVideo = true;^
      } else if (mimeType.includes('audio')) {^
        // Audio^
        fileContent = `[Archivo de audio: ${fileName}]`;^
        result.isAudio = true;^
      } else {^
        // Otros tipos de archivo^
        fileContent = `[Archivo binario: ${fileName}]`;^
        result.isBinary = true;^
      }^
    } catch (processingError) {^
      console.error(`Error procesando el archivo: ${processingError.message}`);^
      result.processingError = processingError.message;^
      result.content = `Error al procesar el archivo: ${processingError.message}`;^
    }^
^
    // Asignar contenido al resultado^
    result.content = fileContent;^
^
    // Eliminar archivo temporal^
    fs.unlinkSync(filePath);^
^
    res.json(result);^
  } catch (error) {^
    console.error(`Error general: ${error.message}`);^
    res.status(500).json({^
      error: `Error al procesar el archivo: ${error.message}`^
    });^
  }^
});^
^
// Ruta de inicio^
app.get('/', (req, res) => {^
  res.send(`^
    <html>^
      <head>^
        <title>MCP Server</title>^
        <style>^
          body { font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }^
          h1 { color: #333; }^
          .container { border: 1px solid #ddd; padding: 20px; border-radius: 5px; }^
          .info { background-color: #f5f5f5; padding: 15px; border-radius: 5px; margin-top: 20px; }^
          .footer { margin-top: 30px; font-size: 12px; color: #777; text-align: center; }^
        </style>^
      </head>^
      <body>^
        <h1>Servidor MCP (Model Context Protocol)</h1>^
        <div class="container">^
          <p>Este servidor permite convertir archivos a formato JSON para proporcionar contexto al modelo de IA Granite.</p>^
          <p>Estado: ^✅ En funcionamiento</p>^
          ^
          <div class="info">^
            <h3>Como usar:</h3>^
            <p>Envia un archivo mediante POST a la ruta /convert con un formulario multipart/form-data.</p>^
            <p>Ejemplo de curl:</p>^
            <pre>curl -F "file=@ruta/al/archivo.pdf" http://localhost:${port}/convert</pre>^
          </div>^
        </div>^
        <div class="footer">^
          <p>MCP Server v1.0 - ${new Date().toISOString()}</p>^
        </div>^
      </body>^
    </html>^
  `);^
});^
^
// Iniciar servidor^
app.listen(port, () => {^
  console.log(`🚀 Servidor MCP iniciado en http://localhost:${port}`);^
  console.log(`📁 Para convertir archivos, envialos a http://localhost:${port}/convert`);^
}); > server.js

REM Instalar dependencias
echo [INFO] Instalando dependencias...
call npm install

REM Crear archivo README.md
echo [INFO] Creando README.md...
echo # Servidor MCP (Model Context Protocol)^
^
Este servidor permite convertir cualquier tipo de archivo (PDF, Word, Excel, etc.) a formato JSON para proporcionar contexto al modelo de IA Granite.^
^
## Requisitos^
^
- Node.js 14 o superior^
- NPM 6 o superior^
^
## Instalacion^
^
```bash^
# Instalar dependencias^
npm install^
```^
^
## Uso^
^
```bash^
# Iniciar el servidor^
npm start^
```^
^
El servidor se iniciara en http://localhost:3000^
^
## Endpoints^
^
- `GET /`: Pagina de inicio con informacion del servidor^
- `POST /convert`: Convierte un archivo a JSON^
  - Parametros:^
    - `file`: Archivo a convertir (multipart/form-data)^
^
## Ejemplo de uso con cURL^
^
```bash^
curl -F "file=@ruta/al/archivo.pdf" http://localhost:3000/convert^
```^
^
## Tipos de archivos soportados^
^
- PDF (application/pdf)^
- Word (application/vnd.openxmlformats-officedocument.wordprocessingml.document, application/msword)^
- Excel (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/vnd.ms-excel)^
- Texto (text/plain, text/html, text/css, text/javascript)^
- JSON (application/json)^
- XML (application/xml)^
- Imagenes (image/*)^
- Videos (video/*)^
- Audio (audio/*) > README.md

REM Crear script de inicio
echo [INFO] Creando script de inicio para Windows...
echo @echo off^
echo Iniciando servidor MCP...^
node server.js^
pause > start_mcp_server.bat

echo.
echo [OK] Instalacion completada!
echo.
echo Para iniciar el servidor MCP:
echo   - Ejecuta: start_mcp_server.bat
echo.
echo El servidor estara disponible en: http://localhost:3000
echo Los archivos se pueden convertir enviandolos a: http://localhost:3000/convert
echo.
echo IMPORTANTE: Este servidor debe estar ejecutandose para que la app
echo pueda convertir archivos a formato JSON para el modelo Granite.
echo.

pause
