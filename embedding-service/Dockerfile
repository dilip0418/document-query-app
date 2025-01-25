# Use Python 3.10.5 as the base image
# This provides an isolated Python environment by default
FROM python:3.10.5-slim

# Set working directory in container
WORKDIR /app

# Copy requirements file
# This should be generated from your local venv using: pip freeze > requirements.txt
COPY requirements.txt .

# Install dependencies
# Docker containers are isolated by nature, so we don't need a virtual environment
# All packages will be installed in an isolated container environment
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application
COPY . .

# Expose the port the app runs on
EXPOSE 8000

# Command to run the application
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]