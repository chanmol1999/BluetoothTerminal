#include <SoftwareSerial.h>

SoftwareSerial bluetooth(10, 11); // RX, TX   11: RX of HC05; 10: TX of HC05
int LED = 13; // the on-board LED
char data; // the data received
String command;

void setup() {
  bluetooth.begin(9600);
  Serial.begin(9600);
  Serial.println("Waiting for command...");
  bluetooth.println("Send 'turn on' to turn on the LED. Send 'turn off' to turn Off");
  pinMode(LED, OUTPUT);
}

void loop() {
  if (bluetooth.available()) { //wait for data received

    while (true) {
      data = bluetooth.read();
      if (data == '#') break;
      command = command + data;
      delay(1);
    }

    if (command == "turn on") {

      digitalWrite(LED, 1);
      Serial.println("LED On !");
      bluetooth.println("LED On !");

    } else if (command == "turn off") {

      digitalWrite(LED, 0);
      Serial.println("LED Off !");
      bluetooth.println("LED Off ! ");

    } else if (command == "hello" || command == "hi") {

      bluetooth.println("Hi there, this is Arduino !");

    }  else if (command == "how are you?" || command == "how are you") {

      bluetooth.println("I'm fine, you can now give commands");

    } else {

      bluetooth.println("Sorry, I don't understand !");

    }
  }
  command = "";
  delay(100);
}
