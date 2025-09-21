var Wearable = {};

  /**
   * Configure the plugin with the capability name and path to be used for communication
   * between the app and the watch.
   *
   * @param capability  String
   *   The capability name that is configured in the WearOS app. This is used to find the watch
   *   to communicate with.
   * @param path  String
   *   The path that will be used for communication between the app and the watch.
   * @param success  Success callback 
   *  This callback will be called when the plugin has been successfully configured
   * @param error  Error callback 
   *  This callback will be called if there is an error
   */
    Wearable.configure = function(capability, path, success, error) {
        cordova.exec(success, error, 'Wearable', 'configure', [capability, path]);
    };

/**
   * Provides a callback method that will be called when the watch sends data to the app or the session state changes.
   *
   * @param success Success callback
   *   This callback will be called when the watch sends data to the app or the session state changes.
   *   The callback will receive a single parameter which is an object containing the data sent from the watch.
   * @param error  Error callback 
   *  This callback will be called if there is an error
   */
Wearable.registerListener = function(success, error) {
    cordova.exec(success, error, 'Wearable', 'registerListener', []);
};

 /**
   * Sends data to the watch.
   * @param data  data to send to the watch
   *   - key: String - the key for the data
   *   - value: String - the value for the data
   *   - type: String - the type of data being sent. One of: 'string', 'number', 'boolean', 'object'
   *     (object should be a JSON serializable object)
   * @param success  Success callback 
   *  This callback will be called when the data has been successfully sent to the watch
   * @param error  Error callback 
   *  This callback will be called if there is an error
   */
Wearable.sendDataToWatch = function(data, success, error) {
    cordova.exec(success, error, 'Wearable', 'sendDataToWatch', [data]);
};

module.exports = Wearable;
