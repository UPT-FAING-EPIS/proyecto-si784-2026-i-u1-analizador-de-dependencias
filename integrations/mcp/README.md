# DepAnalyzer MCP Server

Servidor MCP local que expone el analizador de dependencias mediante `stdio`.

## Herramientas

- `analyze_dependencies`: audita dependencias sin modificar archivos.
- `plan_dependency_updates`: genera sugerencias e identificadores estables.
- `apply_dependency_updates`: aplica solo identificadores aprobados explícitamente.

## Desarrollo

```bash
npm install
npm run build
npm test
```

El servidor busca el CLI en `DEPANALYZER_BIN`, en la distribución local de Gradle y finalmente en
`PATH`.

## Configuración

Después de `npm run build`, configure el cliente MCP para ejecutar:

```json
{
  "mcpServers": {
    "depanalyzer": {
      "command": "node",
      "args": ["RUTA_ABSOLUTA/integrations/mcp/dist/index.js"],
      "env": {
        "DEPANALYZER_BIN": "RUTA_ABSOLUTA_AL_EJECUTABLE"
      }
    }
  }
}
```

`OSS_INDEX_TOKEN` y `NVD_API_KEY` pueden añadirse al mismo bloque `env`. No deben escribirse en
argumentos ni reportes.
