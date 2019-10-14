FROM node as build
WORKDIR /usr/src/app
COPY . ./
RUN npm run build
FROM nginx
COPY --from=build /usr/src/app/build /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]