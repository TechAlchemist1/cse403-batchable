export default function Loading({children}: React.PropsWithChildren<{}>) {
  return (
    <p className="mb-4 text-sm text-gray-600 dark:text-gray-300">{children}</p>
  );
}
