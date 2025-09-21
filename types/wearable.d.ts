/**
 * Type definitions for cordova-plugin-wearable
 *
 * The plugin exposes a global `Wearable` object (and module export) whose methods mirror
 * the JavaScript facade in `www/wearable.js`. All APIs support both Promises and the
 * traditional Cordova callback style. Shared-state queries return canonical payloads
 * across Android and iOS as documented in the README.
 */

declare namespace CordovaWearable {
  type JsonPrimitive = string | number | boolean | null;
  interface JsonObject { [key: string]: JsonValue; }
  interface JsonArray extends Array<JsonValue> {}
  type JsonValue = JsonPrimitive | JsonObject | JsonArray;

  interface ConfigureOptions {
    /** Android: capability advertised by the Wear OS watch app (required on Android). */
    capability?: string;
    /** Android: base message path used for phone↔watch communication (required on Android). */
    path?: string;
    /** Android: foreground service notification title. */
    notificationTitle?: string;
    /** Android: foreground service notification text. */
    notificationText?: string;
    /** Enable native debug logging (cross-platform). */
    enableLogging?: boolean;
    /** Legacy alias for enableLogging. */
    enableDebugLogging?: boolean;
  }

  interface SupportResult { isSupported: boolean; }
  interface PairingResult { isPaired: boolean; }
  interface ConnectivityResult { isConnected: boolean; }
  interface StateQueryResult { exists: boolean; value?: JsonValue; }
  interface HasStateResult { has: boolean; }
  type StateMap = JsonObject;

  interface StateChangedEvent {
    event: 'stateChanged';
    state: StateMap;
  }

  interface ListenerRegistration {
    listening: true;
  }

  type MessageListener = (payload: JsonObject) => void;
  type EventListener = (event: any) => void;
  type StateListener = (event: StateChangedEvent) => void;
  type ErrorCallback = (error: any) => void;

  interface Wearable {
    // Configuration / connectivity lifecycle
    configure(options?: ConfigureOptions): Promise<void>;
    configure(options: ConfigureOptions | undefined, success: () => void, error?: ErrorCallback): void;

    startConnectivity(): Promise<void>;
    startConnectivity(success: () => void, error?: ErrorCallback): void;

    stopConnectivity(): Promise<void>;
    stopConnectivity(success: () => void, error?: ErrorCallback): void;

    // Messaging listeners
    registerMessageListener(): Promise<void>;
    registerMessageListener(listener: MessageListener): Promise<ListenerRegistration>;
    registerMessageListener(listener: MessageListener, error: ErrorCallback): void;

    registerEventListener(): Promise<void>;
    registerEventListener(listener: EventListener): Promise<ListenerRegistration>;
    registerEventListener(listener: EventListener, error: ErrorCallback): void;

    // Messaging
    sendMessageToWatch(data: JsonObject): Promise<void>;
    sendMessageToWatch(data: JsonObject, success: () => void, error?: ErrorCallback): void;

    // Platform state
    isSupported(): Promise<SupportResult>;
    isSupported(success: (result: SupportResult) => void, error?: ErrorCallback): void;

    isPaired(): Promise<PairingResult>;
    isPaired(success: (result: PairingResult) => void, error?: ErrorCallback): void;

    isConnected(): Promise<ConnectivityResult>;
    isConnected(success: (result: ConnectivityResult) => void, error?: ErrorCallback): void;

    // Shared State Sync
    registerStateListener(): Promise<ListenerRegistration>;
    registerStateListener(listener: StateListener): Promise<ListenerRegistration>;
    registerStateListener(listener: StateListener, error: ErrorCallback): void;

    unregisterStateListener(): Promise<void>;
    unregisterStateListener(success: () => void, error?: ErrorCallback): void;

    setState(key: string, value: JsonValue): Promise<void>;
    setState(key: string, value: JsonValue, success: () => void, error?: ErrorCallback): void;

    removeState(key: string): Promise<void>;
    removeState(key: string, success: () => void, error?: ErrorCallback): void;

    getState(key: string): Promise<StateQueryResult>;
    getState(key: string, success: (result: StateQueryResult) => void, error?: ErrorCallback): void;

    hasState(key: string): Promise<HasStateResult>;
    hasState(key: string, success: (result: HasStateResult) => void, error?: ErrorCallback): void;

    setStatePath(pointer: string, value: JsonValue): Promise<void>;
    setStatePath(pointer: string, value: JsonValue, success: () => void, error?: ErrorCallback): void;

    removeStatePath(pointer: string): Promise<void>;
    removeStatePath(pointer: string, success: () => void, error?: ErrorCallback): void;

    getAllState(): Promise<StateMap>;
    getAllState(success: (state: StateMap) => void, error?: ErrorCallback): void;
  }
}

/** Global object injected by the plugin (window.Wearable). */
declare const Wearable: CordovaWearable.Wearable;

declare global {
  interface Window {
    Wearable: CordovaWearable.Wearable;
  }
}

export = Wearable;

declare module 'cordova-plugin-wearable' {
  export = Wearable;
}
