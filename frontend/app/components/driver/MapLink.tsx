interface Props {
  link: string;
}

export default function MapLink({link}: Props) {
  return (
    <p>
      Click{' '}
      <a href={link} className="text-blue-500 underline">
        here
      </a>{' '}
      to view your route.
    </p>
  );
}
