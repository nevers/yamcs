import { SpaceSystem } from './mdb';
import { Alarm, Event, IndexGroup, Range, Sample } from './monitoring';
import { Bucket, ClientInfo, CommandQueue, Instance, InstanceTemplate, Link, Processor, Record, RocksDbDatabase, Service, Stream, Table, UserInfo } from './system';


export type WebSocketClientMessage = [
  number, // Protocol
  number, // Message Type
  number, // Request Sequence
  { [key: string]: string } // payload
];

export type WebSocketServerMessage = [
  number, // Protocol
  number, // Message Type
  number, // Response Sequence
  {
    dt?: string
    data?: { [key: string]: any }

    et?: string
    msg?: string
  }
];

export interface EventsWrapper {
  event: Event[];
}

export interface InstancesWrapper {
  instance: Instance[];
}

export interface InstanceTemplatesWrapper {
  template: InstanceTemplate[];
}

export interface LinksWrapper {
  link: Link[];
}

export interface ServicesWrapper {
  service: Service[];
}

export interface SpaceSystemsWrapper {
  spaceSystem: SpaceSystem[];
}

export interface AlarmsWrapper {
  alarm: Alarm[];
}

export interface UsersWrapper {
  users: UserInfo[];
}

export interface ClientsWrapper {
  client: ClientInfo[];
}

export interface CommandQueuesWrapper {
  queue: CommandQueue[];
}

export interface ProcessorsWrapper {
  processor: Processor[];
}

export interface StreamsWrapper {
  stream: Stream[];
}

export interface TablesWrapper {
  table: Table[];
}

export interface RecordsWrapper {
  record: Record[];
}

export interface SamplesWrapper {
  sample: Sample[];
}

export interface RangesWrapper {
  range: Range[];
}

export interface SourcesWrapper {
  source: string[];
}

export interface IndexResult {
  group: IndexGroup[];
}

export interface PacketNameWrapper {
  name: string[];
}

export interface BucketsWrapper {
  bucket: Bucket[];
}

export interface RocksDbDatabasesWrapper {
  database: RocksDbDatabase[];
}
