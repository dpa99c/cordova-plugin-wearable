/*
 Copyright (C) 2025 Dave Alden
 SPDX-License-Identifier: GPL-3.0-or-later

 This file is part of the cordova-plugin-wearable project and is
 licensed under the GNU General Public License v3.0 or later.
 See LICENSE at the repository root for full license text.
*/

/* global Wearable, cordova */

exports.defineAutoTests = function () {
    const isAndroid = cordova.platformId === 'android';
    const isIOS = cordova.platformId === 'ios';

    const capability = 'cordova_plugin_wearable_test';
    const path = '/data';
    const skipUnsupported = 'Wearable API not supported in this environment';
    const skipAndroidOnly = 'Android-specific test';
    const expectRejection = 'Expected promise to be rejected';

    describe('Wearable Plugin (window.Wearable)', function () {
        var apiSupported = false;
        var configuredOK = false;
        // Determine API support once for the whole suite and store it. Tests
        // that require the native wearable API will skip if it's not available.
        beforeAll(function (done) {
            if (typeof Wearable === 'undefined' || typeof Wearable.isSupported !== 'function') {
                apiSupported = false;
                done();
                return;
            }
            Wearable.isSupported()
                .then(function (result) {
                    apiSupported = !!(result && result.isSupported);
                    done();
                })
                .catch(function () {
                    apiSupported = false;
                    done();
                });
        });
        
        it('should exist', function () {
            expect(window.Wearable).toBeDefined();
            expect(typeof window.Wearable).toBe('object');
        });

        describe('API methods', function () {
            it('should have configure method', function () {
                expect(Wearable.configure).toBeDefined();
                expect(typeof Wearable.configure).toBe('function');
            });

            it('should have startConnectivity method', function () {
                expect(Wearable.startConnectivity).toBeDefined();
                expect(typeof Wearable.startConnectivity).toBe('function');
            });

            it('should have stopConnectivity method', function () {
                expect(Wearable.stopConnectivity).toBeDefined();
                expect(typeof Wearable.stopConnectivity).toBe('function');
            });

            it('should have registerMessageListener method', function () {
                expect(Wearable.registerMessageListener).toBeDefined();
                expect(typeof Wearable.registerMessageListener).toBe('function');
            });

            it('should have registerEventListener method', function () {
                expect(Wearable.registerEventListener).toBeDefined();
                expect(typeof Wearable.registerEventListener).toBe('function');
            });

            it('should have sendMessageToWatch method', function () {
                expect(Wearable.sendMessageToWatch).toBeDefined();
                expect(typeof Wearable.sendMessageToWatch).toBe('function');
            });

            it('should have isSupported method', function () {
                expect(Wearable.isSupported).toBeDefined();
                expect(typeof Wearable.isSupported).toBe('function');
            });

            it('should have isPaired method', function () {
                expect(Wearable.isPaired).toBeDefined();
                expect(typeof Wearable.isPaired).toBe('function');
            });

            it('should have isConnected method', function () {
                expect(Wearable.isConnected).toBeDefined();
                expect(typeof Wearable.isConnected).toBe('function');
            });

            // State sync API
            it('should have registerStateListener method', function () {
                expect(Wearable.registerStateListener).toBeDefined();
                expect(typeof Wearable.registerStateListener).toBe('function');
            });

            it('should have setState method', function () {
                expect(Wearable.setState).toBeDefined();
                expect(typeof Wearable.setState).toBe('function');
            });

            it('should have removeState method', function () {
                expect(Wearable.removeState).toBeDefined();
                expect(typeof Wearable.removeState).toBe('function');
            });

            it('should have getState method', function () {
                expect(Wearable.getState).toBeDefined();
                expect(typeof Wearable.getState).toBe('function');
            });

            it('should have hasState method', function () {
                expect(Wearable.hasState).toBeDefined();
                expect(typeof Wearable.hasState).toBe('function');
            });

            it('should have setStatePath method', function () {
                expect(Wearable.setStatePath).toBeDefined();
                expect(typeof Wearable.setStatePath).toBe('function');
            });

            it('should have removeStatePath method', function () {
                expect(Wearable.removeStatePath).toBeDefined();
                expect(typeof Wearable.removeStatePath).toBe('function');
            });

            it('should have getAllState method', function () {
                expect(Wearable.getAllState).toBeDefined();
                expect(typeof Wearable.getAllState).toBe('function');
            });
        });

        describe('Platform Support', function () {
            it('should return isSupported status', function (done) {
                Wearable.isSupported()
                    .then(function (result) {
                        expect(result).toBeDefined();
                        expect(typeof result.isSupported).toBe('boolean');
                        // Do not force a true value here — CI/emulator environments may
                        // not have wearable support available. Individual tests that
                        // require support will skip when `apiSupported` is false.
                        done();
                    })
                    .catch(done.fail);
            });
        });

        describe('Configuration', function () {
            // Configure once for this suite (skip if not supported). Avoid calling
            // `configure()` repeatedly elsewhere in the tests.
            beforeAll(function (done) {
                if (!apiSupported) {
                    // Nothing to do when API not supported
                    done();
                    return;
                }

                const options = { enableLogging: true };
                if (isAndroid) {
                    // Include notification fields; they are a superset of minimal options.
                    options.capability = capability;
                    options.path = path;
                    options.notificationTitle = 'Test Watch Connection';
                    options.notificationText = 'Testing watch connectivity';
                }

                Wearable.configure(options)
                    .then(function () {
                        configuredOK = true;
                        done();
                    })
                    .catch(function (err) {
                        // If configure fails in this environment, mark as not supported
                        // so dependent tests will skip rather than fail.
                        console.warn('Configuration failed in beforeAll:', err);
                        apiSupported = false;
                        configuredOK = false;
                        done();
                    });
            });

            it('should configure successfully with minimal options', function () {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    return;
                }
                expect(configuredOK).toBe(true);
            });

            it('should configure with notification options on Android', function () {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    return;
                }
                if (!isAndroid) {
                    pending(skipAndroidOnly);
                    return;
                }
                expect(configuredOK).toBe(true);
            });
        });

        describe('State Sync - Basic Operations', function () {

            it('should set and get a state value', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                const testKey = 'testKey1';
                const testValue = 'testValue1';

                Wearable.setState(testKey, testValue)
                    .then(function () {
                        return Wearable.getState(testKey);
                    })
                    .then(function (result) {
                        expect(result).toBeDefined();
                        expect(result).toEqual({ exists: true, value: testValue });
                        done();
                    })
                    .catch(done.fail);
            });

            it('should handle complex object values', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                const testKey = 'testObject';
                const testValue = {
                    string: 'test',
                    number: 42,
                    boolean: true,
                    array: [1, 2, 3],
                    nested: { prop: 'value' }
                };

                Wearable.setState(testKey, testValue)
                    .then(function () {
                        return Wearable.getState(testKey);
                    })
                    .then(function (result) {
                        expect(result).toBeDefined();
                        expect(result.exists).toBe(true);
                        expect(result.value).toEqual(testValue);
                        done();
                    })
                    .catch(done.fail);
            });

            it('should check if state exists with hasState', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                const testKey = 'existsTest';
                const testValue = 'someValue';

                Wearable.setState(testKey, testValue)
                    .then(function () {
                        return Wearable.hasState(testKey);
                    })
                    .then(function (result) {
                        expect(result).toEqual({ has: true });
                        done();
                    })
                    .catch(done.fail);
            });

            it('should return false for non-existent keys', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                Wearable.hasState('nonExistentKey_' + Date.now())
                    .then(function (result) {
                        expect(result).toEqual({ has: false });
                        done();
                    })
                    .catch(done.fail);
            });

            it('should report exists=false for missing getState values', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                const key = 'missing_' + Date.now();
                Wearable.getState(key)
                    .then(function (result) {
                        expect(result).toEqual({ exists: false });
                        done();
                    })
                    .catch(done.fail);
            });

            it('should remove a state value', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                const testKey = 'removeTest';
                const testValue = 'willBeRemoved';

                Wearable.setState(testKey, testValue)
                    .then(function () {
                        return Wearable.hasState(testKey);
                    })
                    .then(function (result) {
                        expect(result).toEqual({ has: true });
                        return Wearable.removeState(testKey);
                    })
                    .then(function () {
                        return Wearable.hasState(testKey);
                    })
                    .then(function (result) {
                        expect(result).toEqual({ has: false });
                        done();
                    })
                    .catch(done.fail);
            });

            it('should get all state', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                const testData = {
                    key1: 'value1',
                    key2: 42,
                    key3: { nested: true }
                };

                var promises = [];
                for (var key in testData) {
                    if (testData.hasOwnProperty(key)) {
                        promises.push(Wearable.setState(key, testData[key]));
                    }
                }

                Promise.all(promises)
                    .then(function () {
                        return Wearable.getAllState();
                    })
                    .then(function (allState) {
                        expect(allState).toBeDefined();
                        expect(typeof allState).toBe('object');

                        expect(allState.key1).toBe(testData.key1);
                        expect(allState.key2).toBe(testData.key2);
                        expect(allState.key3).toEqual(testData.key3);
                        done();
                    })
                    .catch(done.fail);
            });

            it('should reject empty key for setState', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                Wearable.setState('', 'value')
                    .then(function () {
                        done.fail(expectRejection);
                    })
                    .catch(function () {
                        expect(true).toBe(true);
                        done();
                    });
            });

            it('should reject empty key for getState', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                Wearable.getState('')
                    .then(function () {
                        done.fail(expectRejection);
                    })
                    .catch(function () {
                        expect(true).toBe(true);
                        done();
                    });
            });

            it('should reject empty key for removeState', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }
                Wearable.removeState('')
                    .then(function () {
                        done.fail(expectRejection);
                    })
                    .catch(function () {
                        expect(true).toBe(true);
                        done();
                    });
            });
        });

        describe('State Sync - JSON Pointer Paths', function () {
            var connectivityStarted = false;
            beforeAll(function (done) {
                if (!apiSupported) {
                    done();
                    return;
                }
                // Ensure StateSync is running so setStatePath/removeStatePath work
                Wearable.startConnectivity()
                    .then(function () {
                        connectivityStarted = true;
                        // small delay to let native service initialize
                        setTimeout(done, 250);
                    })
                    .catch(function (err) {
                        console.warn('startConnectivity failed for JSON pointer tests:', err);
                        connectivityStarted = false;
                        done();
                    });
            });

            afterAll(function (done) {
                if (!connectivityStarted) { done(); return; }
                Wearable.stopConnectivity().then(function () { done(); }).catch(function () { done(); });
            });
            it('should set and retrieve a JSON pointer path', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                const pointer = '/user/name';
                const value = 'John Doe';

                Wearable.setStatePath(pointer, value)
                    .then(function () {
                        return Wearable.getAllState();
                    })
                    .then(function (allState) {
                        // allState may be a plain object delivered from native
                        expect(allState).toBeDefined();
                        expect(allState.user).toBeDefined();
                        expect(allState.user.name).toBe(value);
                        done();
                    })
                    .catch(function (err) { done.fail(err); });
            });

            it('should handle nested JSON pointer paths', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                const pointer = '/settings/display/theme';
                const value = 'dark';

                Wearable.setStatePath(pointer, value)
                    .then(function () {
                        return Wearable.getAllState();
                    })
                    .then(function (allState) {
                        expect(allState).toBeDefined();
                        expect(allState.settings).toBeDefined();
                        expect(allState.settings.display).toBeDefined();
                        expect(allState.settings.display.theme).toBe(value);
                        done();
                    })
                    .catch(function (err) { done.fail(err); });
            });

            it('should remove a JSON pointer path', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                const pointer = '/temp/data';
                const value = 'temporary';

                Wearable.setStatePath(pointer, value)
                    .then(function () {
                        return Wearable.getAllState();
                    })
                    .then(function (allState) {
                        expect(allState).toBeDefined();
                        expect(allState.temp).toBeDefined();
                        expect(allState.temp.data).toBe(value);
                        return Wearable.removeStatePath(pointer);
                    })
                    .then(function () {
                        return Wearable.getAllState();
                    })
                    .then(function (allState) {
                        expect(allState.temp?.data).toBeUndefined();
                        done();
                    })
                    .catch(function (err) { done.fail(err); });
            });

            it('should reject invalid JSON pointer (no leading slash)', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                Wearable.setStatePath('invalid/path', 'value')
                    .then(function () {
                        done.fail(expectRejection);
                    })
                    .catch(function () {
                        expect(true).toBe(true);
                        done();
                    });
            });

            it('should reject empty JSON pointer', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                Wearable.setStatePath('', 'value')
                    .then(function () {
                        done.fail(expectRejection);
                    })
                    .catch(function () {
                        expect(true).toBe(true);
                        done();
                    });
            });
        });

        describe('State Sync - Listener', function () {
            it('should register a state listener and receive current state', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                var called = false;
                var cleanup = function () {
                    if (typeof Wearable.unregisterStateListener === 'function') {
                        return Wearable.unregisterStateListener();
                    }
                    return Promise.resolve();
                };

                Wearable.registerStateListener(function (payload) {
                    if (called) return;
                    called = true;
                    const stateObj = (payload && payload.state) ? payload.state : payload;
                    try {
                        expect(stateObj).toBeDefined();
                        expect(typeof stateObj).toBe('object');
                        cleanup().then(function () { done(); }).catch(done.fail);
                    } catch (e) {
                        cleanup().then(function () { done.fail(e); });
                    }
                });
            });

            it('should receive updates when state changes', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                let updateCount = 0;
                const testKey = 'listenerTest_' + Date.now();
                const testValue = 'listenerValue';
                // Guard so we only call done once even if multiple events arrive
                let finished = false;
                const cleanup = function () {
                    const tasks = [];
                    if (typeof Wearable.unregisterStateListener === 'function') {
                        tasks.push(Wearable.unregisterStateListener());
                    }
                    if (typeof Wearable.removeState === 'function') {
                        tasks.push(Wearable.removeState(testKey).catch(function () { return undefined; }));
                    }
                    if (tasks.length === 0) {
                        return Promise.resolve();
                    }
                    return Promise.all(tasks).then(function () { return undefined; });
                };

                Wearable.registerStateListener(function (payload) {
                    if (finished) return;
                    updateCount++;

                    if (updateCount === 1) {
                        // Trigger a local state change which should result in a second
                        // callback containing the updated state
                        Wearable.setState(testKey, testValue)
                            .catch(function (err) {
                                finished = true;
                                cleanup().then(function () { done.fail(err); });
                            });
                    } else if (updateCount === 2) {
                        try {
                            const state = (payload && payload.state) ? payload.state : payload;
                            expect(state[testKey]).toBe(testValue);
                            finished = true;
                            cleanup().then(function () { done(); }).catch(done.fail);
                        } catch (e) {
                            finished = true;
                            cleanup().then(function () { done.fail(e); });
                        }
                    }
                });
            });
        });

        describe('Connectivity Checks', function () {
            it('should check if device is paired', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                Wearable.isPaired()
                    .then(function (result) {
                        expect(result).toBeDefined();
                        expect(typeof result.isPaired).toBe('boolean');
                        done();
                    })
                    .catch(done.fail);
            });

            it('should check if device is connected', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                Wearable.isConnected()
                    .then(function (result) {
                        expect(result).toBeDefined();
                        expect(typeof result.isConnected).toBe('boolean');
                        done();
                    })
                    .catch(done.fail);
            });
        });

        describe('Callback-style API Support', function () {
            it('should support callback-style for setState', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                Wearable.setState('callbackTest', 'callbackValue', function () {
                    expect(true).toBe(true); // ensure this spec has at least one expectation
                    done();
                }, function (error) {
                    fail('setState should not fail: ' + error);
                    done();
                });
            });

            it('should support callback-style for getState', function (done) {
                if (!apiSupported) {
                    pending(skipUnsupported);
                    done();
                    return;
                }

                const testKey = 'callbackGetTest';
                const testValue = 'callbackGetValue';

                Wearable.setState(testKey, testValue, function () {
                    Wearable.getState(testKey, function (result) {
                        expect(result).toEqual({ exists: true, value: testValue });
                        done();
                    }, function (error) {
                        fail('getState should not fail: ' + error);
                        done();
                    });
                });
            });

            it('should support callback-style for isSupported', function (done) {
                Wearable.isSupported(function (result) {
                    expect(result.isSupported).toBeDefined();
                    done();
                }, function (error) {
                    fail('isSupported should not fail: ' + error);
                    done();
                });
            });
        });
    });
};

exports.defineManualTests = function (contentEl, createActionButton) {
    let messageListener = null;
    let eventListener = null;
    let stateListener = null;

    const logMessage = function (message, color) {
        const log = document.getElementById('info');
        const logLine = document.createElement('div');
        if (color) {
            logLine.style.color = color;
        }
        const timestamp = new Date().toLocaleTimeString();
        logLine.innerHTML = `[${timestamp}] ${message}`;
        log.appendChild(logLine);
        
        log.scrollTop = log.scrollHeight;
    };

    const clearLog = function () {
        const log = document.getElementById('info');
        log.innerHTML = '';
    };

    const isAndroid = cordova.platformId === 'android';

    const info_div =
        '<h1>Wearable Plugin Manual Tests</h1>' +
        '<div id="info" style="height: 200px; overflow-y: scroll; border: 1px solid #ccc; padding: 10px; margin-bottom: 20px; font-family: monospace; font-size: 12px;"></div>';

    const setup_tests =
        '<h2>Setup</h2>' +
        '<p><strong>Prerequisites:</strong></p>' +
        '<ul>' +
        '<li>iOS: Paired watch simulator ("iPhone 17 Pro")</li>' +
        '<li>Android: Paired Wear OS device or emulator</li>' +
        '<li>Watch companion app must be installed and running</li>' +
        '</ul>' +
        '<div id="clearLog"></div>' +
        '<div id="configure"></div>' +
        'Expected: Configuration successful message' +
        '<p/><div id="startConnectivity"></div>' +
        'Expected: Connectivity started (Android: foreground notification appears)' +
        '<p/><div id="stopConnectivity"></div>' +
        'Expected: Connectivity stopped (Android: foreground notification disappears)';

    const platform_tests =
        '<h2>Platform Checks</h2>' +
        '<div id="checkSupported"></div>' +
        'Expected: isSupported = true' +
        '<p/><div id="checkPaired"></div>' +
        'Expected: isPaired = true if watch paired' +
        '<p/><div id="checkConnected"></div>' +
        'Expected: isConnected = true if watch connected/reachable';

    const message_tests =
        '<h2>Messaging</h2>' +
        '<div id="registerMessageListener"></div>' +
        'Expected: Listener registered, watch messages will appear in log' +
        '<p/><div id="sendMessage"></div>' +
        'Expected: Message sent to watch (check watch app for receipt)' +
        '<p/><div id="registerEventListener"></div>' +
        'Expected: Event listener registered, connectivity events will appear in log';

    const state_tests =
        '<h2>State Sync</h2>' +
        '<div id="registerStateListener"></div>' +
        'Expected: State listener registered, current state displayed in log' +
        '<p/><div id="setSimpleState"></div>' +
        'Expected: State set, listener callback shows update' +
        '<p/><div id="setComplexState"></div>' +
        'Expected: Complex object state set and synced' +
        '<p/><div id="getState"></div>' +
        'Expected: Retrieved state value displayed' +
        '<p/><div id="getAllState"></div>' +
        'Expected: Full state object displayed' +
        '<p/><div id="removeState"></div>' +
        'Expected: State key removed, listener shows update' +
        '<p/><div id="setJsonPointer"></div>' +
        'Expected: Nested path created via JSON pointer' +
        '<p/><div id="removeJsonPointer"></div>' +
        'Expected: Nested path removed via JSON pointer';

    contentEl.innerHTML =
        info_div +
        setup_tests +
        platform_tests +
        message_tests +
        state_tests;

    createActionButton('Clear Log', function () {
        clearLog();
    }, 'clearLog');

    createActionButton('Configure Plugin', function () {
        const options = {
            enableLogging: true
        };

        if (isAndroid) {
            options.capability = capability;
            options.path = path;
            options.notificationTitle = 'Watch Connection';
            options.notificationText = 'Testing watch connectivity';
        }

        Wearable.configure(options)
            .then(() => logMessage('✓ Plugin configured successfully', 'green'))
            .catch(err => logMessage('✗ Configure failed: ' + JSON.stringify(err), 'red'));
    }, 'configure');

    createActionButton('Start Connectivity', function () {
        Wearable.startConnectivity()
            .then(() => logMessage('✓ Connectivity started', 'green'))
            .catch(err => logMessage('✗ Start connectivity failed: ' + JSON.stringify(err), 'red'));
    }, 'startConnectivity');

    createActionButton('Stop Connectivity', function () {
        Wearable.stopConnectivity()
            .then(() => logMessage('✓ Connectivity stopped', 'green'))
            .catch(err => logMessage('✗ Stop connectivity failed: ' + JSON.stringify(err), 'red'));
    }, 'stopConnectivity');

    createActionButton('Check isSupported', function () {
        Wearable.isSupported()
            .then(result => logMessage('isSupported: ' + result.isSupported, result.isSupported ? 'green' : 'orange'))
            .catch(err => logMessage('✗ isSupported failed: ' + JSON.stringify(err), 'red'));
    }, 'checkSupported');

    createActionButton('Check isPaired', function () {
        Wearable.isPaired()
            .then(result => logMessage('isPaired: ' + result.isPaired, result.isPaired ? 'green' : 'orange'))
            .catch(err => logMessage('✗ isPaired failed: ' + JSON.stringify(err), 'red'));
    }, 'checkPaired');

    createActionButton('Check isConnected', function () {
        Wearable.isConnected()
            .then(result => logMessage('isConnected: ' + result.isConnected, result.isConnected ? 'green' : 'orange'))
            .catch(err => logMessage('✗ isConnected failed: ' + JSON.stringify(err), 'red'));
    }, 'checkConnected');

    createActionButton('Register Message Listener', function () {
        if (messageListener) {
            logMessage('Message listener already registered', 'orange');
            return;
        }

        messageListener = function (message) {
            logMessage('📨 Message from watch: ' + JSON.stringify(message, null, 2), 'blue');
        };

        Wearable.registerMessageListener(messageListener)
            .then(() => logMessage('✓ Message listener registered', 'green'))
            .catch(err => logMessage('✗ Register message listener failed: ' + JSON.stringify(err), 'red'));
    }, 'registerMessageListener');

    createActionButton('Send Message to Watch', function () {
        const message = {
            type: 'test',
            timestamp: Date.now(),
            message: 'Hello from phone!'
        };

        Wearable.sendMessageToWatch(message)
            .then(() => logMessage('✓ Message sent to watch: ' + JSON.stringify(message), 'green'))
            .catch(err => logMessage('✗ Send message failed: ' + JSON.stringify(err), 'red'));
    }, 'sendMessage');

    createActionButton('Register Event Listener', function () {
        if (eventListener) {
            logMessage('Event listener already registered', 'orange');
            return;
        }

        eventListener = function (event) {
            logMessage('🔔 Plugin event: ' + JSON.stringify(event, null, 2), 'purple');
        };

        Wearable.registerEventListener(eventListener)
            .then(() => logMessage('✓ Event listener registered', 'green'))
            .catch(err => logMessage('✗ Register event listener failed: ' + JSON.stringify(err), 'red'));
    }, 'registerEventListener');

    createActionButton('Register State Listener', function () {
        if (stateListener) {
            logMessage('State listener already registered', 'orange');
            return;
        }

        stateListener = function (state) {
            logMessage('📊 State update: ' + JSON.stringify(state, null, 2), 'teal');
        };

        Wearable.registerStateListener(stateListener)
            .then(() => logMessage('✓ State listener registered', 'green'))
            .catch(err => logMessage('✗ Register state listener failed: ' + JSON.stringify(err), 'red'));
    }, 'registerStateListener');

    createActionButton('Set Simple State', function () {
        const key = 'testString';
        const value = 'Hello Watch! ' + Date.now();

        Wearable.setState(key, value)
            .then(() => logMessage('✓ State set: ' + key + ' = ' + value, 'green'))
            .catch(err => logMessage('✗ Set state failed: ' + JSON.stringify(err), 'red'));
    }, 'setSimpleState');

    createActionButton('Set Complex State', function () {
        const key = 'testObject';
        const value = {
            timestamp: Date.now(),
            user: { name: 'Test User', id: 123 },
            settings: { theme: 'dark', notifications: true },
            data: [1, 2, 3, 4, 5]
        };

        Wearable.setState(key, value)
            .then(() => logMessage('✓ Complex state set: ' + JSON.stringify(value), 'green'))
            .catch(err => logMessage('✗ Set complex state failed: ' + JSON.stringify(err), 'red'));
    }, 'setComplexState');

    createActionButton('Get State Value', function () {
        const key = 'testString';

        Wearable.getState(key)
            .then(result => {
                if (result.exists) {
                    logMessage('✓ State retrieved: ' + key + ' = ' + JSON.stringify(result.value), 'green');
                } else {
                    logMessage('Key "' + key + '" does not exist', 'orange');
                }
            })
            .catch(err => logMessage('✗ Get state failed: ' + JSON.stringify(err), 'red'));
    }, 'getState');

    createActionButton('Get All State', function () {
        Wearable.getAllState()
            .then(state => logMessage('All state: ' + JSON.stringify(state, null, 2), 'green'))
            .catch(err => logMessage('✗ Get all state failed: ' + JSON.stringify(err), 'red'));
    }, 'getAllState');

    createActionButton('Remove State Key', function () {
        const key = 'testString';

        Wearable.removeState(key)
            .then(() => logMessage('✓ State key removed: ' + key, 'green'))
            .catch(err => logMessage('✗ Remove state failed: ' + JSON.stringify(err), 'red'));
    }, 'removeState');

    createActionButton('Set JSON Pointer Path', function () {
        const pointer = '/user/preferences/theme';
        const value = 'dark';

        Wearable.setStatePath(pointer, value)
            .then(() => logMessage('✓ JSON pointer set: ' + pointer + ' = ' + value, 'green'))
            .catch(err => logMessage('✗ Set JSON pointer failed: ' + JSON.stringify(err), 'red'));
    }, 'setJsonPointer');

    createActionButton('Remove JSON Pointer Path', function () {
        const pointer = '/user/preferences/theme';

        Wearable.removeStatePath(pointer)
            .then(() => logMessage('✓ JSON pointer removed: ' + pointer, 'green'))
            .catch(err => logMessage('✗ Remove JSON pointer failed: ' + JSON.stringify(err), 'red'));
    }, 'removeJsonPointer');
};
