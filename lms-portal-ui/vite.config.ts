import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";
import { componentTagger } from "lovable-tagger";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  server: {
    host: "::",
    port: 8080,
    hmr: {
      overlay: false,
    },
    proxy: {
      "/proxy/auth": {
        target: "http://localhost:8086",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/auth/, ""),
      },
      "/proxy/products": {
        target: "http://localhost:8087",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/products/, ""),
      },
      "/proxy/loan-applications": {
        target: "http://localhost:8088",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/loan-applications/, ""),
      },
      "/proxy/loans": {
        target: "http://localhost:8089",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/loans/, ""),
      },
      "/proxy/payments": {
        target: "http://localhost:8090",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/payments/, ""),
      },
      "/proxy/accounting": {
        target: "http://localhost:8091",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/accounting/, ""),
      },
      "/proxy/float": {
        target: "http://localhost:8092",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/float/, ""),
      },
      "/proxy/collections": {
        target: "http://localhost:8093",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/collections/, ""),
      },
      "/proxy/compliance": {
        target: "http://localhost:8094",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/compliance/, ""),
      },
      "/proxy/reporting": {
        target: "http://localhost:8095",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/reporting/, ""),
      },
      "/proxy/scoring": {
        target: "http://localhost:8096",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/proxy\/scoring/, ""),
      },
    },
  },
  plugins: [react(), mode === "development" && componentTagger()].filter(Boolean),
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
    dedupe: ["react", "react-dom", "react/jsx-runtime", "@tanstack/react-query"],
  },
  optimizeDeps: {
    include: ["react", "react-dom", "@tanstack/react-query"],
  },
}));
