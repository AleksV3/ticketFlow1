import {defineConfig} from "@playwright/test";
const port=process.env.PLAYWRIGHT_PORT??"3200";
export default defineConfig({testDir:"./e2e",use:{baseURL:`http://127.0.0.1:${port}`,trace:"retain-on-failure"},webServer:{command:`npm run dev -- --webpack --hostname 127.0.0.1 --port ${port}`,url:`http://127.0.0.1:${port}/login`,reuseExistingServer:true,timeout:120000,env:{NEXT_PUBLIC_API_BASE_URL:`http://127.0.0.1:${port}/api`}}});
