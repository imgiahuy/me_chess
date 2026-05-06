import { defineConfig } from "vite";
// @ts-ignore
import react from "@vitejs/plugin-react";

export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            "/v1": {
                target: "http://localhost:8080",
                changeOrigin: true
            }
        }
    }
});