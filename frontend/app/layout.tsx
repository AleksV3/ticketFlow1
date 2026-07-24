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
    <html lang="en">
      <body className={manrope.variable}><script dangerouslySetInnerHTML={{__html:`(function(){try{var t=localStorage.getItem('ticketflow1-theme')||'SYSTEM';var l=t==='LIGHT'||(t==='SYSTEM'&&matchMedia('(prefers-color-scheme: light)').matches);document.documentElement.dataset.theme=l?'light':'dark';document.documentElement.style.colorScheme=l?'light':'dark'}catch(e){}})()`}} />{children}<DevLogPanel /></body>
    </html>
  );
}
