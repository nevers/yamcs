import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, first, map, take } from 'rxjs/operators';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { SubscriptionModel } from './SubscriptionModel';
import { WebSocketServerMessage } from './types/internal';
import { Alarm, AlarmSubscriptionResponse, Event, EventSubscriptionResponse, ParameterData, ParameterSubscriptionRequest, ParameterSubscriptionResponse, TimeInfo, TimeSubscriptionResponse } from './types/monitoring';
import { AlarmSubscriptionRequest, ClientInfo, ClientSubscriptionResponse, CommandQueue, CommandQueueEvent, CommandQueueEventSubscriptionResponse, CommandQueueSubscriptionResponse, ConnectionInfo, ConnectionInfoSubscriptionResponse, Instance, InstanceSubscriptionResponse, LinkEvent, LinkSubscriptionResponse, Processor, ProcessorSubscriptionRequest, ProcessorSubscriptionResponse, Statistics, StatisticsSubscriptionResponse, StreamData, StreamEvent, StreamEventSubscriptionResponse, StreamSubscriptionResponse } from './types/system';

const PROTOCOL_VERSION = 1;
const MESSAGE_TYPE_REQUEST = 1;
const MESSAGE_TYPE_REPLY = 2;
const MESSAGE_TYPE_EXCEPTION = 3;
const MESSAGE_TYPE_DATA = 4;

/**
 * Automatically reconnecting web socket client. It also
 * transfers subscriptions between different connections.
 */
export class WebSocketClient {

  readonly connected$ = new BehaviorSubject<boolean>(false);

  private subscriptionModel: SubscriptionModel;
  private webSocket: WebSocketSubject<{}>;

  private webSocketConnection$: Observable<{}>;
  private webSocketConnectionSubscription: Subscription;

  // Server-controlled metadata on the connected client
  // (clientId, instance, processor)
  private connectionInfo$ = new BehaviorSubject<ConnectionInfo | null>(null);

  private requestSequence = 0;

  constructor(baseHref: string, instance?: string) {
    const currentLocation = window.location;
    let url = 'ws://';
    if (currentLocation.protocol === 'https:') {
      url = 'wss://';
    }
    url += `${currentLocation.host}${baseHref}_websocket`;
    if (instance) {
      url += `/${instance}`;
    }

    this.subscriptionModel = new SubscriptionModel();
    this.webSocket = webSocket({
      url,
      protocol: 'json',
      closeObserver: {
        next: () => {
          this.connected$.next(false);
        }
      },
      openObserver: {
        next: () => {
          // Note we do not set connected$ here
          // Instead prefer to set that after
          // receiving the initial bootstrap message
        }
      }
    });
    this.webSocketConnection$ = this.webSocket.pipe(
      // retryWhen(errors => {
      //  console.log('Cannot connect to Yamcs');
      //  return errors.pipe(delay(1000));
      //}),
    );
    this.webSocketConnectionSubscription = this.webSocketConnection$.subscribe(
      (msg: WebSocketServerMessage) => {
        if (!this.connected$.value && msg[1] === MESSAGE_TYPE_DATA && msg[3].dt === 'CONNECTION_INFO') {
          const connectionInfo = msg[3].data as ConnectionInfo;
          this.connectionInfo$.next(connectionInfo);
          this.connected$.next(true);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          console.error(`Server error:  ${msg[3].et}`, msg[3].msg);
        }
      },
      (err: any) => console.log(err)
    );
  }

  async getEventUpdates() {
    this.subscriptionModel.events = true;
    const requestId = this.emit({ events: 'subscribe' });

    return new Promise<EventSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as EventSubscriptionResponse;
          response.event$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'EVENT'),
            map(msg => msg[3].data as Event),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      })
    });
  }

  async getTimeUpdates() {
    this.subscriptionModel.time = true;
    const requestId = this.emit({ time: 'subscribe' });

    return new Promise<TimeSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = msg[3].data as TimeSubscriptionResponse;
          response.timeInfo$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'TIME_INFO'),
            map(msg => msg[3].data as TimeInfo),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getConnectionInfoUpdates() {
    // No need to emit anything for this, all clients receive these events.
    return new Promise<ConnectionInfoSubscriptionResponse>((resolve, reject) => {
      // Wait for the intial connection info data to arrive. Yamcs will always
      // sent this as the first data packet of a new connection.
      this.connectionInfo$.pipe(
        filter(connectionInfo => connectionInfo != null),
        take(1),
      ).subscribe(connectionInfo => {
        const response = {} as ConnectionInfoSubscriptionResponse;
        response.connectionInfo = connectionInfo!;
        response.connectionInfo$ = this.webSocketConnection$.pipe(
          filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
          filter((msg: WebSocketServerMessage) => msg[3].dt === 'CONNECTION_INFO'),
          map(msg => msg[3].data as ConnectionInfo),
        );
        resolve(response);
      }, err => reject(err))
    });
  }

  async getLinkUpdates(instance?: string) {
    this.subscriptionModel.links = true;
    const requestId = this.emit({ links: 'subscribe' });

    return new Promise<LinkSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as LinkSubscriptionResponse;
          response.linkEvent$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'LINK_EVENT'),
            map(msg => msg[3].data as LinkEvent),
            filter((linkEvent: LinkEvent) => {
              return !instance || (instance === linkEvent.linkInfo.instance);
            }),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getStreamEventUpdates(instance: string) {
    this.subscriptionModel.streams = true;
    const requestId = this.emit({
      streams: 'subscribe',
      data: { instance },
    });

    return new Promise<StreamEventSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as StreamEventSubscriptionResponse;
          response.streamEvent$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'STREAM_EVENT'),
            map(msg => msg[3].data as StreamEvent),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getAlarmUpdates(options?: AlarmSubscriptionRequest) {
    this.subscriptionModel.alarms = true;
    const requestId = this.emit({ alarms: 'subscribe', data: options });

    return new Promise<AlarmSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as AlarmSubscriptionResponse;
          response.alarm$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt!.endsWith('ALARM_DATA')),
            map(msg => msg[3].data as Alarm),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getClientUpdates(instance?: string) {
    this.subscriptionModel.management = true;
    const requestId = this.emit({
      management: 'subscribe',
    });

    return new Promise<ClientSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as ClientSubscriptionResponse;
          response.client$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'CLIENT_INFO'),
            map(msg => msg[3].data as ClientInfo),
            filter((clientInfo: ClientInfo) => {
              return !instance || (instance === clientInfo.instance);
            }),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getStreamUpdates(stream: string) {
    this.subscriptionModel.stream = true;
    this.subscriptionModel.streamName = stream;
    const requestId = this.emit({
      stream: 'subscribe',
      data: { stream },
    });

    return new Promise<StreamSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as StreamSubscriptionResponse;
          response.streamData$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'STREAM_DATA'),
            map(msg => msg[3].data as StreamData),
            filter(streamData => streamData.stream === stream),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getInstanceUpdates() {
    this.subscriptionModel.instance = true;
    const requestId = this.emit({ instance: 'subscribe' });

    return new Promise<InstanceSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as InstanceSubscriptionResponse;
          response.instance$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'INSTANCE'),
            map(msg => msg[3].data as Instance),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getProcessorUpdates(options?: ProcessorSubscriptionRequest) {
    this.subscriptionModel.processor = true;
    const requestId = this.emit({ processor: 'subscribe', data: options });

    return new Promise<ProcessorSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = msg[3].data as ProcessorSubscriptionResponse;
          response.processor$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'PROCESSOR_INFO'),
            map(msg => msg[3].data as Processor),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getProcessorStatistics(instance?: string) {
    this.subscriptionModel.management = true;
    const requestId = this.emit({
      management: 'subscribe',
    });

    return new Promise<StatisticsSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as StatisticsSubscriptionResponse;
          response.statistics$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'PROCESSING_STATISTICS'),
            map(msg => msg[3].data as Statistics),
            filter((statistics: Statistics) => {
              return !instance || (instance === statistics.instance);
            }),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getCommandQueueUpdates(instance?: string, processor?: string) {
    this.subscriptionModel.commandQueues = true;
    const requestId = this.emit({ cqueues: 'subscribe' });

    return new Promise<CommandQueueSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as CommandQueueSubscriptionResponse;
          response.commandQueue$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'COMMAND_QUEUE_INFO'),
            map(msg => msg[3].data as CommandQueue),
            filter((commandQueue: CommandQueue) => {
              return !instance || (instance === commandQueue.instance);
            }),
            filter((commandQueue: CommandQueue) => {
              return !processor || (processor === commandQueue.processorName);
            }),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getCommandQueueEventUpdates(instance?: string, processor?: string) {
    this.subscriptionModel.commandQueues = true;
    const requestId = this.emit({ cqueues: 'subscribe' });

    return new Promise<CommandQueueEventSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as CommandQueueEventSubscriptionResponse;
          response.commandQueueEvent$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'COMMAND_QUEUE_EVENT'),
            map(msg => msg[3].data as CommandQueueEvent),
            filter((commandQueueEvent: CommandQueueEvent) => {
              return !instance || (instance === commandQueueEvent.data.instance);
            }),
            filter((commandQueueEvent: CommandQueueEvent) => {
              return !processor || (processor === commandQueueEvent.data.processorName);
            }),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getParameterValueUpdates(options: ParameterSubscriptionRequest) {
    this.subscriptionModel.parameters = options;
    const requestId = this.emit({
      parameter: 'subscribe',
      data: options,
    });

    return new Promise<ParameterSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = msg[3].data as ParameterSubscriptionResponse;

          // Turn SubscribedParameters into a more convenient mapping
          response.mapping = {};
          if (response.subscribed) {
            for (const subscribedParameter of response.subscribed) {
              response.mapping[subscribedParameter.numericId] = subscribedParameter.id;
            }
          }

          response.parameterValues$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'PARAMETER'),
            map(msg => msg[3].data as ParameterData),
            filter(pdata => pdata.subscriptionId === response.subscriptionId),
            map(pdata => pdata.parameter),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  close() {
    this.webSocketConnectionSubscription.unsubscribe();
    this.webSocket.unsubscribe();
  }

  private emit(payload: { [key: string]: any, data?: {} }) {
    this.webSocket.next([
      PROTOCOL_VERSION,
      MESSAGE_TYPE_REQUEST,
      ++this.requestSequence,
      payload,
    ]);
    return this.requestSequence
  }
}
