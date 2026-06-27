# Changelog

## 0.1.3

- Centro visual para seleccionar y preparar una o varias actualizaciones.
- Confirmacion explicita, copia temporal, comparacion de cambios y reanalisis posterior.
- Versiones desconocidas presentadas como `Version no detectada`, sin actualizaciones inseguras.
- Accion para activar el analisis dinamico y resolver versiones administradas por Maven o Gradle.
- Panel de detalle ampliado con impacto, recomendacion, datos tecnicos y colores por severidad.
- Mensajes claros cuando el CLI instalado no permite planes de actualizacion seguros.

## 0.1.2

- Vista lateral agrupada por severidad, dependencias desactualizadas y hallazgos sin ubicacion.
- Panel visual de detalle para vulnerabilidades y actualizaciones.
- Acciones desde la vista para abrir archivo, ver referencia CVE y aplicar actualizaciones sugeridas.
- Estados vacios y errores mas claros.
- Icono monocromatico optimizado para la barra de actividad de VS Code.

## 0.1.1

- Compatibilidad automática con versiones del CLI que generan `dependency-report.json`.
- Mensajes claros cuando el CLI todavía no admite planes o aplicación de actualizaciones.

## 0.1.0

- Primera publicación pública de DepAnalyzer Security.
- Vista lateral de dependencias.
- Diagnósticos, hover y Quick Fix.
- Análisis automático, manual y al guardar.
- Soporte para Maven, Gradle, npm, Poetry y requirements.
