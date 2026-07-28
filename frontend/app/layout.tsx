import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Manrope } from "next/font/google";
import { DevLogPanel } from "@/components/DevLogPanel";
import "@xyflow/react/dist/style.css";
import "./globals.css";

const manrope = Manrope({ subsets: ["latin"], variable: "--font-manrope" });

export const metadata: Metadata = {
  title: "TicketFlow1",
  description: "TicketFlow1 ticketing frontend scaffold"
};

type RootLayoutProps = Readonly<{
  children: ReactNode;
}>;

export default function RootLayout({ children }: RootLayoutProps) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={manrope.variable}><script dangerouslySetInnerHTML={{__html:`(function(){try{var p=localStorage.getItem('ticketflow1-theme')||'DARK';var t=p==='LIGHT'?'light':p==='SYSTEM'&&window.matchMedia('(prefers-color-scheme: light)').matches?'light':'dark';document.documentElement.dataset.theme=t;document.documentElement.style.colorScheme=t}catch(e){document.documentElement.dataset.theme='dark';document.documentElement.style.colorScheme='dark'}})()`}} />{children}<DevLogPanel /></body>
    </html>
  );
}
