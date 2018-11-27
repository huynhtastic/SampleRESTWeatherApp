import datetime
import os
import re
import time

from pdb import set_trace as st

import aioblescan as aiobs
import asyncio
import paho.mqtt.client as mqtt
from aioblescan.plugins import EddyStone


history_file = os.path.join(os.path.dirname(__file__), 'step_history.csv')
broker_address = '192.168.4.1'
publish_topic = 'goalTopic'
subscribe_topic = 'weatherTopic'
yesterday_topic = 'yesterdayTopic'
prompt_topic = 'promptTopic'
today = datetime.datetime.now().date()
goal = 999999  # goal needs to be calculated
steps = 0

def _yesterday_goal_met():
    yst = (datetime.datetime.now() - datetime.timedelta(days=1)).date()
    with open(history_file, 'r') as inf:
        for line in inf:
            entry = [item.strip() for item in line.split(',')]
            try:
                dt = datetime.datetime.strptime(entry[0], '%Y-%m-%d').date()
                if dt == yst:
                    return entry[1] >= entry[2]
                elif dt > yst:
                    break
            except:
                pass
        return 'Yesterday not found'

def _process_packet(data):
    ev = aiobs.HCI_Event()  # event is passed to HCI in between the BT controller and host stack
    xx = ev.decode(data)  # decode the signal
    xx = EddyStone().decode(ev)  # run our decoded packet through the EddyStone protocol
    if xx:
        match = re.match(r'.*(group3p).*steps=(\d*).*', xx['url'])
        # if we match, we parse the steps and publish it to the broker
        if match:
            print('Received Eddystone Beacon Steps. Processing...')
            today = datetime.datetime.now().date()
            steps = match.groups()[1]
            print('Publishing steps...')
            client.publish(publish_topic, _determine_goal_met())
            with open(history_file, 'r') as inf:
                lines = inf.read().strip()
            with open(history_file, 'w') as outf:
                new_entry = '{},{},{}'.format(str(today),steps,goal)
                found = False
                for line in lines.split('\n'):
                    line = line.strip()
                    entry = [item.strip() for item in line.split(',')]
                    if entry[0] == str(today):
                        found = True
                        outf.write(new_entry)
                    else:
                        outf.write(line)
                    outf.write('\n')

                if not found:
                    outf.write(new_entry)

def _determine_goal_met():
    return steps >= goal

def _on_mqtt_message_received(client, userdata, message):
    global goal
    payload = str(message.payload.decode('utf-8'))
    print("message received\n" + payload)
    if payload:
        goal = _predict_steps(payload)
    print('Today\'s goal: {} steps'.format(goal))
    print('User has {} steps recorded\n'.format(steps))
    client.publish(yesterday_topic, _yesterday_goal_met())
    client.publish(publish_topic, _determine_goal_met())

def _predict_steps(inp):
    numbers_regex = r'.*\s([\d\.]+).*'
    inp = [float(re.match(numbers_regex, line).groups()[0]) for line in inp.split('\n')[3:-1]]
    weights = [41.13028054, -11.34748777, 28.91362573]
    steps = 0
    for i in range(3):
        steps += weights[i] * inp[i]
    return round(steps + 1215.23927059)

if __name__ == '__main__':
    mydev = 0
    event_loop = asyncio.get_event_loop()  # creates event loop to run async tasks
    mysocket = aiobs.create_bt_socket(mydev)  # creates a bt socket
    fac = event_loop._create_connection_transport(mysocket, aiobs.BLEScanRequester, None, None)  # create a transport level connection between the bluetooth and socket
    conn, btctrl = event_loop.run_until_complete(fac)  # runs the future to get the bluetooth and connection objects
    btctrl.process = _process_packet  # assigns our custom process packet function to the bluetooth processing
    btctrl.send_scan_request()  # start scanning via bluetooth

    # create mqtt client
    client = mqtt.Client('P2')
    client.connect(broker_address)
    client.on_message = _on_mqtt_message_received
    client.loop_start()
    client.subscribe(subscribe_topic)
    client.subscribe(prompt_topic)

    try:
        event_loop.run_forever()  # runs our bluetooth scan until we send a keyboard interrupt signal
    except KeyboardInterrupt:
        print('keyboard interrupt')
    finally:
        print('Stopping MQTT broadcast...')
        client.loop_stop()
        print('MQTT broadcast stopped.')
        print('closing event loop')
        btctrl.stop_scan_request()  # stop BT scans
        conn.close()  # close the socket connection
        event_loop.close()  # stop our event_loop from taking in more async tasks
