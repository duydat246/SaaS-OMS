/**
 * Dev server proxy: /api và /auth (POST, v.v.) → backend :8080.
 * GET/HEAD /auth/login hay /auth/register là route Angular — không proxy,
 * nếu không backend nhận GET và trả lỗi (vd. 500 JSON).
 */
module.exports = {
  "/api": {
    target: "http://localhost:8080",
    secure: false,
  },
  "/auth": {
    target: "http://localhost:8080",
    secure: false,
    bypass(req) {
      if (req.method === "GET" || req.method === "HEAD") {
        return "/index.html";
      }
    },
  },
};
