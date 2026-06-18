import { defineConfig } from "vite";
// @ts-ignore
import react from "@vitejs/plugin-react";

export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            "/v1/players": {
                target: "http://localhost:8090",
                changeOrigin: true
            },
            "/v1": {
                target: "http://localhost:8085",
                changeOrigin: true
            }
        }
    }
});