#include <ArduinoBLE.h>

/*****************************************************************************************
************************ INIZIALIZZAZIONE VARIABILI BLE **********************************
*****************************************************************************************/


unsigned long currentTime;
const char* deviceServiceUuid = "19b10000-e8f2-537e-4f6c-d104768a1214";
const char* deviceServiceCharacteristicUuid = "19b10001-e8f2-537e-4f6c-d104768a1214";

unsigned long previousTimeBLE = millis(); // Utilizzato per il multitasking
long timeIntervalBLE = 50;

/*****************************************************************************************
************************ INIZIALIZZAZIONE VARIABILI SENSORE VELOCITA' ********************
*****************************************************************************************/


// Definizione dei pin per il sensore di distanza ad ultrasuoni
const int trigPin = 9;
const int echoPin = 10;
const unsigned int maxDistance = 200; // Massima distanza di rilevamento in cm (esempio)

long previousDistance = 0; // Memorizza la distanza precedente
unsigned long previousTime = 0; // Memorizza il tempo precedente
unsigned long previousTimeSpeedSensor = millis(); // Utilizzato per il multitasking
long timeIntervalSpeedSensor = 50;

long duration, distance;
float velocita;

void setup() {
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);

  Serial.begin(9600);
  while (!Serial);
 
  if (!BLE.begin()) {
    Serial.println("Inizializzazione del BLE fallita!");
    while (1);
  }
 
  BLE.setLocalName("Nano 33 BLE (Central)"); 
  BLE.advertise();

  Serial.println("Arduino Nano 33 BLE Sense (Central Device)");
  Serial.println(" ");

}
 
void loop() {
  connectToPeripheral();
}

void connectToPeripheral() {
  BLEDevice peripheral;
  
  Serial.println("- Discovering peripheral device...");

  do
  {
    BLE.scanForUuid(deviceServiceUuid);
    peripheral = BLE.available();
  } while (!peripheral);
  
  if (peripheral) {
    Serial.println("* Peripheral device found!");
    Serial.print("* Device MAC address: ");
    Serial.println(peripheral.address());
    Serial.print("* Device name: ");
    Serial.println(peripheral.localName());
    Serial.print("* Advertised service UUID: ");
    Serial.println(peripheral.advertisedServiceUuid());
    Serial.println(" ");
    BLE.stopScan();
    controlPeripheral(peripheral);
  }
}

void controlPeripheral(BLEDevice peripheral) {

  Serial.println("- Connecting to peripheral device...");

  if (peripheral.connect()) {
    Serial.println("* Connected to peripheral device!");
    Serial.println(" ");
  } else {
    Serial.println("* Connection to peripheral device failed!");
    Serial.println(" ");
    return;
  }

  Serial.println("- Discovering peripheral device attributes...");
  if (peripheral.discoverAttributes()) {
    Serial.println("* Peripheral device attributes discovered!");
    Serial.println(" ");
  } else {
    Serial.println("* Peripheral device attributes discovery failed!");
    Serial.println(" ");
    peripheral.disconnect();
    return;
  }

  BLECharacteristic myCharacteristic = peripheral.characteristic(deviceServiceCharacteristicUuid); 

  if (!myCharacteristic) {
      Serial.println("* Peripheral device does not have data_type characteristic!");
      peripheral.disconnect();
      return;
  } 
  else if (!myCharacteristic.canWrite()) {
      Serial.println("* Peripheral does not have a writable data_type characteristic!");
      peripheral.disconnect();
      return;
   }

  while(peripheral.connected()) {
    
    currentTime = millis(); // Ottiene il tempo corrente

    digitalWrite(trigPin, LOW);
    delayMicroseconds(2);
    digitalWrite(trigPin, HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPin, LOW);

    duration = pulseIn(echoPin, HIGH);
    distance = duration / 58;

    if(distance > 0) {
      if(previousTime > 0) { // Assicurati che non sia il primo ciclo
        // Calcola la velocità con la direzione invertita
        velocita = ((float)(previousDistance - distance) / (currentTime - previousTime)) * 1000 * 0.036;
        if(velocita < 0) {
          velocita = 0;
        }

        if( (velocita > 20) || (distance < 10) )  {
          Serial.println("invio segnale pericolo medio");
          if(myCharacteristic.writeValue("pericolo_medio")) {
            Serial.println("Message successfully sent to peripheral");
          }
          else {
            Serial.println("failed to write value to peripheral");
          }
        }
        else if ( (velocita > 20) && (distance < 10) ) {
          Serial.println("invio segnale pericolo alto");
          if(myCharacteristic.writeValue("pericolo_alto")) {
            Serial.println("Message successfully sent to peripheral");
          }
          else {
            Serial.println("failed to write value to peripheral");
          }
        }
        else {
          Serial.print(velocita, 2); // Mostra la velocità con 2 decimali
          Serial.println(" km/h    "); // Aggiungi spazi per cancellare vecchi valori più lunghi
        }
      }
      previousDistance = distance; // Aggiorna la distanza precedente
      previousTime = currentTime; // Aggiorna il tempo precedente
    } 
    else {
      // Gestione dell'errore o dell'oggetto troppo lontano/vicino
      Serial.println("oggetto non rilevato o troppo vicino");
    }
    delay(50);
  }
}

