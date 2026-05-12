#!/bin/bash
# Download Qwen2.5-0.5B GGUF model for Anaya Android app
# Place this script in project root and run:
#   bash scripts/download-model.sh

MODEL_DIR="app/src/main/assets/models"
MODEL_FILE="$MODEL_DIR/qwen2.5-0.5b-instruct-q4_k_m.gguf"
MODEL_URL="https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
HF_MIRROR="https://hf-mirror.com/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"

mkdir -p "$MODEL_DIR"

if [ -f "$MODEL_FILE" ] && [ -s "$MODEL_FILE" ]; then
    echo "Model already exists at $MODEL_FILE ($(du -h "$MODEL_FILE" | cut -f1))"
    exit 0
fi

echo "Downloading Qwen2.5-0.5B GGUF model (~469MB)..."
echo "  Trying HuggingFace mirror first..."

if command -v wget &>/dev/null; then
    wget -c --timeout=60 "$HF_MIRROR" -O "$MODEL_FILE" || \
    wget -c --timeout=60 "$MODEL_URL" -O "$MODEL_FILE"
elif command -v curl &>/dev/null; then
    curl -L --connect-timeout 60 "$HF_MIRROR" -o "$MODEL_FILE" || \
    curl -L --connect-timeout 60 "$MODEL_URL" -o "$MODEL_FILE"
else
    echo "Error: neither wget nor curl found"
    exit 1
fi

if [ -f "$MODEL_FILE" ] && [ -s "$MODEL_FILE" ]; then
    echo "Download complete: $(du -h "$MODEL_FILE" | cut -f1)"
else
    echo "Download failed!"
    exit 1
fi
