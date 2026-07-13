import {defineConfig} from "@playwright/test";
export default defineConfig({testDir:"./e2e",use:{baseURL:"http://127.0.0.1:3200",trace:"retain-on-failure"},webServer:{command:"npm run dev -- --hostname 127.0.0.1 --port 3200",url:"http://127.0.0.1:3200/login",reuseExistingServer:true,timeout:120000,env:{NEXT_PUBLIC_API_BASE_URL:"http://127.0.0.1:3200/api"}}});
