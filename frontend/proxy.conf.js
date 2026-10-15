try { process.loadEnvFile(); } catch {} // .env es opcional

module.exports = {
  "/api": {
    target: process.env.BACKEND_URL || "http://localhost:8080",
    secure: false,
    changeOrigin: true,
  },
};
