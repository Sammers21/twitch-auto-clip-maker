FROM node as build
WORKDIR /usr/src/app
COPY . ./
RUN PUBLIC_URL=http://static.clip-maker.com:81 npm run build
FROM nginx
COPY --from=build /usr/src/app/build /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]