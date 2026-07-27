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
      <body className={manrope.variable}><script dangerouslySetInnerHTML={{__html:`(function(){document.documentElement.dataset.theme='dark';document.documentElement.style.colorScheme='dark';try{localStorage.removeItem('ticketflow1-theme')}catch(e){}})()`}} />{children}<DevLogPanel /></body>
    </html>
  );
}
