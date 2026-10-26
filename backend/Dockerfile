FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY swarai_backend ./swarai_backend
RUN useradd --create-home swarai
USER swarai
ENV PORT=8080
CMD ["sh", "-c", "uvicorn swarai_backend.main:app --host 0.0.0.0 --port ${PORT} --proxy-headers"]
