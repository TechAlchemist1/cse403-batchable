import {useAuth0} from '@auth0/auth0-react';

export default function LoginButton() {
  const {loginWithRedirect} = useAuth0();

  return (
    <button
      onClick={() => loginWithRedirect()}
      className="px-5 py-2.5 bg-blue-600 text-white font-semibold rounded-lg shadow-md hover:bg-blue-700 transition-all"
    >
      Log In
    </button>
  );
}
