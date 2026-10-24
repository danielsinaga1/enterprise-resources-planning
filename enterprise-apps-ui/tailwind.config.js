import lineClamp from "@tailwindcss/line-clamp";

/** @type {import('tailwindcss').Config} */
export default {
  // src/erp punya Tailwind v4 sendiri (erp.generated.css, di-scope ke .erp-root). Kalau ikut dipindai,
  // utilitas v3 yang namanya sama (mis. -translate-x-1/2 → transform) bertumpuk dengan versi v4
  // (properti translate) sehingga dialog ERP tergeser dua kali dan keluar layar.
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}", "!./src/erp/**"],
  theme: {
    extend: {
      fontFamily: {
        poppins: ["Poppins", "sans-serif"],
        inter: ["Inter", "sans-serif"],
        jakarta: ["Plus Jakarta Sans", "sans-serif"],
      },
      colors: {
        primary: "#004E7E",
        sec: "#008DE4",
      },
    },
  },
  plugins: [lineClamp],
};
