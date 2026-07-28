import type { NextConfig } from "next";

const backendHost = process.env.BACKEND_HOST;
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;

function apiOrigin() {
  try {
    return apiBaseUrl ? new URL(apiBaseUrl).origin : backendHost ? `https://${backendHost}` : "";
  } catch {
    return "";
  }
}

const connectSources = ["'self'", apiOrigin()].filter(Boolean).join(" ");
const scriptSources = process.env.NODE_ENV === "production"
  ? "'self' 'unsafe-inline'"
  : "'self' 'unsafe-inline' 'unsafe-eval'";
const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src ${scriptSources}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  `connect-src ${connectSources}`,
  "worker-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "frame-ancestors 'none'",
  "form-action 'self'",
].join("; ");

const nextConfig: NextConfig = {
  async headers() {
    return [{
      source: "/:path*",
      headers: [
        { key: "Content-Security-Policy", value: contentSecurityPolicy },
        { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
        { key: "X-Content-Type-Options", value: "nosniff" },
        { key: "X-Frame-Options", value: "DENY" },
        { key: "Permissions-Policy", value: "camera=(), geolocation=(), microphone=(), payment=(), usb=()" },
      ],
    }];
  },
  async rewrites() {
    if (!backendHost) return [];
    return [{
      source: "/api/:path*",
      destination: `https://${backendHost}/api/:path*`,
    }];
  },
};

export default nextConfig;
