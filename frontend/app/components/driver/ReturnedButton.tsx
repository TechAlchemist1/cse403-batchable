import {driverApi} from '~/api/endpoints/driver';
import Button from '../Button';
import {useContext} from 'react';
import {DriverTokenContext} from '../DriverTokenContext';

export default function ReturnedButton() {
  const token = useContext(DriverTokenContext);

  const completeRoute = async () => {
    if (!token || !(await driverApi.markReturned(token))) {
      alert('Failed to complete route');
    }
  };
  return (
    <Button style="emerald" onClick={completeRoute}>
      Complete Route
    </Button>
  );
}
