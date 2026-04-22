#!/usr/bin/env sh

set -eu

REPOSITORY="${DEPANALYZER_REPOSITORY:-UPT-FAING-EPIS/proyecto-si784-2026-i-u1-analizador-de-dependencias}"
VERSION="latest"
INSTALL_DIR="${DEPANALYZER_INSTALL_DIR:-$HOME/.local/bin}"

usage() {
  cat <<'EOF'
Instalador de depanalyzer (binario nativo)

Uso:
  sh install.sh [--version <tag|latest>] [--repo <owner/repo>] [--install-dir <dir>]

Opciones:
  --version      Tag del release (ej: v1.0.0). Default: latest
  --repo         Repositorio GitHub (owner/repo)
  --install-dir  Directorio de instalacion del binario
  -h, --help     Mostrar esta ayuda

Variables de entorno:
  DEPANALYZER_REPOSITORY  Repositorio GitHub (owner/repo)
  DEPANALYZER_INSTALL_DIR Directorio de instalacion
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf '%s\n' "Error: se requiere '$1'" >&2
    exit 1
  fi
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      [ "$#" -ge 2 ] || { echo "Error: --version requiere valor" >&2; exit 1; }
      VERSION="$2"
      shift 2
      ;;
    --repo)
      [ "$#" -ge 2 ] || { echo "Error: --repo requiere valor" >&2; exit 1; }
      REPOSITORY="$2"
      shift 2
      ;;
    --install-dir)
      [ "$#" -ge 2 ] || { echo "Error: --install-dir requiere valor" >&2; exit 1; }
      INSTALL_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Error: opcion desconocida '$1'" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_cmd curl
require_cmd tar
require_cmd mktemp

os_raw="$(uname -s)"
arch_raw="$(uname -m)"

case "$os_raw" in
  Linux) os="linux" ;;
  Darwin) os="macos" ;;
  *)
    echo "Error: sistema operativo no soportado por este instalador: $os_raw" >&2
    echo "Para Windows, descarga el .zip desde GitHub Releases." >&2
    exit 1
    ;;
esac

case "$arch_raw" in
  x86_64|amd64) arch="x64" ;;
  arm64|aarch64) arch="arm64" ;;
  *)
    echo "Error: arquitectura no soportada: $arch_raw" >&2
    exit 1
    ;;
esac

asset="depanalyzer-${os}-${arch}.tar.gz"

if [ "$VERSION" = "latest" ]; then
  download_url="https://github.com/${REPOSITORY}/releases/latest/download/${asset}"
else
  download_url="https://github.com/${REPOSITORY}/releases/download/${VERSION}/${asset}"
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT INT TERM

archive_path="$tmp_dir/$asset"
printf '%s\n' "Descargando ${asset}..."
curl -fsSL "$download_url" -o "$archive_path"

printf '%s\n' "Instalando en ${INSTALL_DIR}..."
mkdir -p "$INSTALL_DIR"
tar -xzf "$archive_path" -C "$tmp_dir"

binary_path="$tmp_dir/depanalyzer"
if [ ! -f "$binary_path" ]; then
  echo "Error: no se encontro el binario dentro del paquete" >&2
  exit 1
fi

install -m 755 "$binary_path" "$INSTALL_DIR/depanalyzer"

printf '%s\n' "Instalacion completada: $INSTALL_DIR/depanalyzer"
if command -v depanalyzer >/dev/null 2>&1; then
  depanalyzer --help >/dev/null 2>&1 || true
  printf '%s\n' "Comando disponible en PATH: depanalyzer"
else
  printf '%s\n' "Agrega este directorio a tu PATH si es necesario: $INSTALL_DIR"
fi
