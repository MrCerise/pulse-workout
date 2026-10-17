import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Interval Coach — Google Play screenshots",
  description: "Neon Athletic Night campaign editor for Interval Coach.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
