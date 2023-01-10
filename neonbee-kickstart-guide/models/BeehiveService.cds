service BeehiveService {
   entity Beehives {
      key id : Integer;
      queen : String;
      drones : Integer;
      worker : Integer;
      population : Integer;
   }
}