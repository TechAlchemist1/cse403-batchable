interface Props {
  onClick?: () => void;
}

/**
 * Represents a clickable card for use in {@link OverviewSection}s (and maybe other places)
 * @param onClick What to do when the card is clicked
 * @param children The contents of the card
 */
export default function Card({
  onClick,
  children,
}: React.PropsWithChildren<Props>) {
  return (
    <button
      onClick={onClick}
      className={`${onClick ? 'cursor-pointer' : ''} w-full text-left p-4 border rounded-lg flex justify-between items-center bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700 gap-1`}
    >
      {children}
    </button>
  );
}
