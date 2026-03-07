interface Props {
  link: string | null;
}

export default function MapLink({link}: Props) {
  return (
    <p>
      Click{' '}
      <a href={link ?? undefined} className="text-blue-500 underline">
        here
      </a>{' '}
      to view your route.
    </p>
  );
}
