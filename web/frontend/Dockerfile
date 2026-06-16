FROM node:20-alpine

WORKDIR /app

# Copy package files first for better caching
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Use nginx to serve the built application
FROM nginx:alpine

# Copy built application to nginx
COPY --from=0 /app/dist /usr/share/nginx/html

# Copy nginx configuration template
COPY nginx.conf /etc/nginx/templates/nginx.conf.template

# Expose port
EXPOSE 3000

# Start nginx with envsubst to substitute environment variables
CMD ["/bin/sh", "-c", "envsubst '$$API_UPSTREAM' < /etc/nginx/templates/nginx.conf.template > /etc/nginx/nginx.conf && nginx -g 'daemon off;'"]
