/*********************************************************************
This is an example for our nRF8001 Bluetooth Low Energy Breakout

  Pick one up today in the adafruit shop!
  ------> http://www.adafruit.com/products/1697

Adafruit invests time and resources providing this open source code, 
please support Adafruit and open-source hardware by purchasing 
products from Adafruit!

Written by Kevin Townsend/KTOWN  for Adafruit Industries.
MIT license, check LICENSE for more information
All text above, and the splash screen below must be included in any redistribution
*********************************************************************/

// This version uses call-backs on the event and RX so there's no data handling in the main loop!

#include <SPI.h>
#include "Adafruit_BLE_UART.h"
#include "icm7218a.h"

#define ADAFRUITBLE_REQ 10
#define ADAFRUITBLE_RDY 2
#define ADAFRUITBLE_RST 9

#define COMMAND 0

#define TIME_SYNC 'S'
#define SET_ALARM 'A'
#define YEAR_MSB 1
#define YEAR_LSB 2
#define MONTH 3
#define DAY 4
#define HOURS 5
#define MINUTES 6
#define SECONDS 7

#define ASCII_ZERO 0x30

Adafruit_BLE_UART uart = Adafruit_BLE_UART(ADAFRUITBLE_REQ, ADAFRUITBLE_RDY, ADAFRUITBLE_RST);

uint8_t clock_hour = 0, clock_minute = 0, clock_second = 0;
uint8_t alarm_hour = 0, alarm_minute = 0, alarm_second = 0;
unsigned long old_millis = 0, new_millis = 0, clock_millis = 0;

bool is_clock_set = false, is_alarm_set = false, clock_on = false, alarm_on = false, alarm_active = false;


ICM7218A time_display = ICM7218A();

void update_clock_display(uint8_t hours, uint8_t minutes);
void update_alarm_display(uint8_t hours, uint8_t minutes);
void blank_clock_display();
void blank_alarm_display();

void toggle_clock_display();
void toggle_alarm_display();

/**************************************************************************/
/*!
    This function is called whenever select ACI events happen
*/
/**************************************************************************/
void aciCallback(aci_evt_opcode_t event)
{
	switch(event)
	{
		case ACI_EVT_DEVICE_STARTED:
			Serial.println(F("Advertising started"));
			break;
		case ACI_EVT_CONNECTED:
			Serial.println(F("Connected!"));
			break;
		case ACI_EVT_DISCONNECTED:
			Serial.println(F("Disconnected or advertising timed out"));
			break;
		default:
			break;
	}
}

/**************************************************************************/
/*!
    This function is called whenever data arrives on the RX channel
*/
/**************************************************************************/
void rxCallback(uint8_t *buffer, uint8_t len)
{
	Serial.print(F("Received "));
	Serial.print(len);
	Serial.print(F(" bytes: "));
	
	for(int i = 0; i < len; i++)
	{
		Serial.print("0x");
		Serial.print((char)buffer[i], HEX); 
		Serial.print(' ');
	}


	char message[100];
	
	switch (buffer[COMMAND])
	{
		case TIME_SYNC:
			clock_hour = (buffer[1] - ASCII_ZERO) * 10 + (buffer[2] - ASCII_ZERO);
			clock_minute = (buffer[3] - ASCII_ZERO) * 10 + (buffer[4] - ASCII_ZERO);
			clock_second = (buffer[5] - ASCII_ZERO) * 10 + (buffer[6] - ASCII_ZERO);
			clock_millis = millis() % 1000;
			
			sprintf(message, "\nTime has been updated to %02d:%02d:%02d.\n", clock_hour, clock_minute, clock_second);
			Serial.print(message);
			
			update_clock_display(clock_hour, clock_minute);
			is_clock_set = true;
			break;
		case SET_ALARM:
			alarm_hour = (buffer[1] - ASCII_ZERO) * 10 + (buffer[2] - ASCII_ZERO);
			alarm_minute = (buffer[3] - ASCII_ZERO) * 10 + (buffer[4] - ASCII_ZERO);
			alarm_second = (buffer[5] - ASCII_ZERO) * 10 + (buffer[6] - ASCII_ZERO);

			sprintf(message, "\nAlarm has been set to %02d:%02d:%02d.\n", alarm_hour, alarm_minute, alarm_second);
			Serial.print(message);
		
			update_alarm_display(alarm_hour, alarm_minute);
			is_alarm_set = true;
			PORTD |= 0x08;
			break;
	}
}

/**************************************************************************/
/*!
    Configure the Arduino and start advertising with the radio
*/
/**************************************************************************/
void setup(void)
{ 
	DDRD |= 0x08;
	PORTD |= 0x08;
	update_clock_display(0, 0);
	update_alarm_display(0, 0);
	
	Serial.begin(9600);
	while(!Serial); // Leonardo/Micro should wait for serial init
	//Serial.println(F("Adafruit Bluefruit Low Energy nRF8001 Callback Echo demo"));

	uart.setRXcallback(rxCallback);
	uart.setACIcallback(aciCallback);
	// uart.setDeviceName("NEWNAME"); /* 7 characters max! */
	uart.begin();

	time_display.turn_on();
}

/**************************************************************************/
/*!
    Constantly checks for new events on the nRF8001
*/
/**************************************************************************/
void loop()
{
	unsigned long delta_millis;
	unsigned long delta_hour, delta_minute, delta_second;
	
	uart.pollACI();
	
	new_millis = millis();
	delta_millis = new_millis - old_millis;


	if (delta_millis >= 500)
	{
		if (!is_clock_set)
		{
			toggle_clock_display();
		}
		else
		{
			delta_second = delta_millis / 1000;
			delta_minute = delta_second / 60;
			delta_hour = delta_minute / 60;
			
			delta_millis %= 1000;
			delta_second %= 60;
			delta_minute %= 60;

			clock_millis += delta_millis;
			clock_second += delta_second + clock_millis / 1000;
			clock_minute += delta_minute + clock_second / 60;
			clock_hour += delta_hour + clock_minute / 60;
			
			clock_millis %= 1000;
			clock_second %= 60;
			clock_minute %= 60;
			clock_hour %= 24;
			
			update_clock_display(clock_hour, clock_minute);
			
			if (!alarm_active && (clock_hour == alarm_hour) && (clock_minute == alarm_minute))
			{
				alarm_active = true;
			}
		}

		if (!is_alarm_set)
		{
			toggle_alarm_display();
			PORTD ^= 0x08;
		}
		else if (alarm_active)
		{
			PORTD ^= 0x08;
		}
		
		old_millis = new_millis;
	}
}

void update_clock_display(uint8_t hours, uint8_t minutes)
{
	if (hours == 0)
	{
		time_display.update_digit(0, HEX_DECODING, 0, false);
	}
	else if ((hours <= 12) && (hours / 10 == 0))
	{
		time_display.update_digit(0, NO_DECODING, hours / 10, false);
	}
	else
	{
		time_display.update_digit(0, HEX_DECODING, hours / 10, false);
	}
	time_display.update_digit(1, HEX_DECODING, hours % 10, true);
	time_display.update_digit(2, HEX_DECODING, minutes / 10 , false);
	time_display.update_digit(3, HEX_DECODING, minutes % 10, false);
	
	clock_on = true;
}

void update_alarm_display(uint8_t hours, uint8_t minutes)
{	
	if (hours == 0)
	{
		time_display.update_digit(4, HEX_DECODING, 0, false);
	}
	else if ((hours <= 12) && (hours / 10 == 0))
	{
		time_display.update_digit(4, NO_DECODING, hours / 10, false);
	}
	else
	{
		time_display.update_digit(4, HEX_DECODING, hours / 10, false);
	}
	time_display.update_digit(5, HEX_DECODING, hours % 10, true);
	time_display.update_digit(6, HEX_DECODING, minutes / 10 , false);
	time_display.update_digit(7, HEX_DECODING, minutes % 10, false);

	alarm_on = true;
}

void toggle_clock_display()
{
	if (clock_on)
	{
		blank_clock_display();
	}
	else
	{
		update_clock_display(0, 0);
	}
}

void toggle_alarm_display()
{
	if (alarm_on)
	{
		blank_alarm_display();
	}
	else
	{
		update_alarm_display(0, 0);
	}
}

void blank_clock_display()
{
	for (int i = 0; i < 4; i++)
	{
		time_display.update_digit(i, NO_DECODING, 0, false);
	}

	clock_on = false;
}

void blank_alarm_display()
{
	for (int i = 4; i < 8; i++)
	{
		time_display.update_digit(i, NO_DECODING, 0, false);
	}
	
	alarm_on = false;
}