/**
 * Landing page placeholder for the Next.js app router.
 *
 * This page exists only to guide users to the authenticated application.
 */
export default function HomePage() {
  return (
    <main className="flex min-h-screen items-center justify-center p-8">
      <section className="card w-full max-w-2xl border-t-[3px] border-t-blue-500 p-10">
        <p className="eyebrow">
          TicketFlow1
        </p>
        <h1 className="mt-4 text-4xl font-bold">
          Frontend scaffold is in place.
        </h1>
        <p className="mt-4 text-base leading-7 text-slate-600">
          This is the default placeholder page for the Next.js App Router setup.
        </p>
        <a
          className="btn-primary mt-8 inline-flex py-3"
          href="/login"
        >
          Open login page
        </a>
      </section>
    </main>
  );
}
