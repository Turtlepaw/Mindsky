import os
import tarfile
import requests
import tensorflow as tf

KAGGLE_URL = "https://www.kaggle.com/api/v1/models/google/universal-sentence-encoder/tensorFlow2/cmlm-en-base/1/download"
ARCHIVE_NAME = "cmlm-en-base.tar.gz"
EXTRACT_DIR = "cmlm-en-base"
TFLITE_FILENAME = "cmlm-en-base.tflite"

# Step 1: Download the model archive from Kaggle
def download_model():
    print("Downloading model...")
    response = requests.get(KAGGLE_URL, stream=True)
    response.raise_for_status()
    with open(ARCHIVE_NAME, "wb") as f:
        for chunk in response.iter_content(chunk_size=8192):
            f.write(chunk)
    print(f"Saved archive as {ARCHIVE_NAME}")

# Step 2: Extract the archive
def extract_model():
    print("Extracting model...")
    with tarfile.open(ARCHIVE_NAME, "r:gz") as tar:
        tar.extractall(EXTRACT_DIR)
    print(f"Extracted to {EXTRACT_DIR}/")

# Step 3: Convert to TensorFlow Lite
def convert_model():
    print("Converting model to TFLite...")
    converter = tf.lite.TFLiteConverter.from_saved_model(EXTRACT_DIR)
    tflite_model = converter.convert()
    with open(TFLITE_FILENAME, "wb") as f:
        f.write(tflite_model)
    print(f"Saved TFLite model as {TFLITE_FILENAME}")

if __name__ == "__main__":
    if not os.path.exists(ARCHIVE_NAME):
        download_model()
    else:
        print(f"{ARCHIVE_NAME} already exists, skipping download.")

    if not os.path.exists(EXTRACT_DIR):
        extract_model()
    else:
        print(f"{EXTRACT_DIR}/ already exists, skipping extraction.")

    convert_model()
