import type { NextConfig } from "next";

const backendHost = process.env.BACKEND_HOST;

const nextConfig: NextConfig = {
  async rewrites() {
    if (!backendHost) return [];
    return [{
      source: "/api/:path*",
      destination: `https://${backendHost}/api/:path*`,
    }];
  },
};

export default nextConfig;
