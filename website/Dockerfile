FROM nginx:1.25-alpine

# Copy built website files
COPY dist/ /usr/share/nginx/html/

# Copy custom nginx config for SPA
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]