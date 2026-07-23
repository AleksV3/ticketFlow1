import type { Metadata } from "next";
import type { ReactNode } from "react";
import { DevLogPanel } from "@/components/DevLogPanel";
import "@xyflow/react/dist/style.css";
import "./globals.css";

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
      <body>{children}<DevLogPanel /></body>
    </html>
  );
}
