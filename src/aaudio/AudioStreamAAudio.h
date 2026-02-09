/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef OBOE_STREAM_AAUDIO_H_
#define OBOE_STREAM_AAUDIO_H_

#include <atomic>
#include <shared_mutex>
#include <mutex>
#include <thread>

#include <common/AdpfWrapper.h>
#include "oboe/AudioStreamBuilder.h"
#include "oboe/AudioStream.h"
#include "oboe/Definitions.h"
#include "AAudioLoader.h"

namespace oboe {

 /**
 * Type used as a key in the AAudioCallbackRegistry lookup table.
 * Passed as userData to AAudio C callbacks.
 */
using callback_token_t = uintptr_t;
static_assert(sizeof(callback_token_t) == sizeof(void *));

/**
 * Sentinel value indicating that the token has not been assigned.
 */
constexpr callback_token_t kCallbackTokenUnspecified = 0;

class AAudioCallbackRegistry;

/**
 * Implementation of OboeStream that uses AAudio.
 *
 * Do not create this class directly.
 * Use an OboeStreamBuilder to create one.
 */
class AudioStreamAAudio : public AudioStream {
    friend class AAudioCallbackRegistry; // allow access to getCallbackToken()
public:
    AudioStreamAAudio();
    explicit AudioStreamAAudio(const AudioStreamBuilder &builder);

    virtual ~AudioStreamAAudio() = default;

    /**
     *
     * @return true if AAudio is supported on this device.
     */
    static bool isSupported();

    // These functions override methods in AudioStream.
    // See AudioStream for documentation.
    Result open() override;
    Result release() override;
    Result close() override;

    Result requestStart() override;
    Result requestPause() override;
    Result requestFlush() override;
    Result requestStop() override;

    ResultWithValue<int32_t> write(const void *buffer,
                  int32_t numFrames,
                  int64_t timeoutNanoseconds) override;

    ResultWithValue<int32_t> read(void *buffer,
                 int32_t numFrames,
                 int64_t timeoutNanoseconds) override;

    ResultWithValue<int32_t> setBufferSizeInFrames(int32_t requestedFrames) override;
    int32_t getBufferSizeInFrames() override;
    ResultWithValue<int32_t> getXRunCount()  override;
    bool isXRunCountSupported() const override { return true; }

    ResultWithValue<double> calculateLatencyMillis() override;

    Result waitForStateChange(StreamState currentState,
                              StreamState *nextState,
                              int64_t timeoutNanoseconds) override;

    Result getTimestamp(clockid_t clockId,
                                       int64_t *framePosition,
                                       int64_t *timeNanoseconds) override;

    StreamState getState() override;

    AudioApi getAudioApi() const override {
        return AudioApi::AAudio;
    }

    DataCallbackResult callOnAudioReady(AAudioStream *stream, void *audioData, int32_t numFrames);

    int32_t callOnPartialAudioReady(AAudioStream *stream, void *audioData, int32_t numFrames);

    bool isMMapUsed();

    void closePerformanceHint() override {
        mAdpfWrapper.close();
        mAdpfOpenAttempted = false;
    }

    oboe::Result reportWorkload(int32_t appWorkload) override {
        if (!isPerformanceHintEnabled()) {
            return oboe::Result::ErrorInvalidState;
        }
        mAdpfWrapper.reportWorkload(appWorkload);
        return oboe::Result::OK;
    }

    oboe::Result notifyWorkloadIncrease(bool cpu, bool gpu, const char* debugName) override {
        if (!isPerformanceHintEnabled()) {
            return oboe::Result::ErrorInvalidState;
        }
        return mAdpfWrapper.notifyWorkloadIncrease(cpu, gpu, debugName);
    }

    oboe::Result notifyWorkloadSpike(bool cpu, bool gpu, const char* debugName) override {
        if (!isPerformanceHintEnabled()) {
            return oboe::Result::ErrorInvalidState;
        }
        return mAdpfWrapper.notifyWorkloadSpike(cpu, gpu, debugName);
    }

    oboe::Result notifyWorkloadReset(bool cpu, bool gpu, const char* debugName) override {
        if (!isPerformanceHintEnabled()) {
            return oboe::Result::ErrorInvalidState;
        }
        return mAdpfWrapper.notifyWorkloadReset(cpu, gpu, debugName);
    }

    Result setOffloadDelayPadding(int32_t delayInFrames, int32_t paddingInFrames) override;
    ResultWithValue<int32_t> getOffloadDelay() override;
    ResultWithValue<int32_t> getOffloadPadding() override;
    Result setOffloadEndOfStream() override;

    ResultWithValue<int64_t> flushFromFrame(
            FlushFromAccuracy accuracy, int64_t positionInFrames) override;

    oboe::Result setPlaybackParameters(const PlaybackParameters& parameters) override;
    ResultWithValue<PlaybackParameters> getPlaybackParameters() override;

protected:
    static void internalErrorCallback(
            AAudioStream *stream,
            void *userData,
            aaudio_result_t error);

    static void internalPresentationEndCallback(
            AAudioStream *stream,
            void *userData);

    void *getUnderlyingStream() const override {
        return mAAudioStream.load();
    }

    void updateFramesRead() override;
    void updateFramesWritten() override;

    void logUnsupportedAttributes();

    void beginPerformanceHintInCallback() override;

    void endPerformanceHintInCallback(int32_t numFrames) override;

    // set by callback (or app when idle)
    std::atomic<bool>    mAdpfOpenAttempted{false};
    AdpfWrapper          mAdpfWrapper;

private:
    /**
     * Get the assigned token used by AAudioCallbackRegistry to lookup a weak_ptr to this stream
     * during callbacks.
     *
     * The token is unique (generated by AAudioCallbackRegistry::allocateCallbackToken) and assigned
     * at construction.
     */
    callback_token_t getCallbackToken() const {
        return mCallbackToken;
    }

    // Must call under mLock. And stream must NOT be nullptr.
    Result requestStop_l(AAudioStream *stream);

    /**
     * Launch a thread that will stop the stream.
     */
    void launchStopThread();

    void updateDeviceIds();

private:

    std::atomic<bool>    mCallbackThreadEnabled;
    std::atomic<bool>    mStopThreadAllowed{false};

    // pointer to the underlying 'C' AAudio stream, valid if open, null if closed
    std::atomic<AAudioStream *> mAAudioStream{nullptr};
    std::shared_mutex           mAAudioStreamLock; // to protect mAAudioStream while closing

    const callback_token_t mCallbackToken;

    static AAudioLoader *mLibLoader;

    // We may not use this but it is so small that it is not worth allocating dynamically.
    AudioStreamErrorCallback mDefaultErrorCallback;
};

/**
 * Singleton registry that safely retains AAudio streams during callbacks.
 *
 * Streams register on open(), deregister on close(), and are locked
 * during callbacks using a token-based lookup.
 */
class AAudioCallbackRegistry {
public:
    static AAudioCallbackRegistry &getInstance() {
        static AAudioCallbackRegistry instance;
        return instance;
    }

    AAudioCallbackRegistry(const AAudioCallbackRegistry &) = delete;
    AAudioCallbackRegistry &operator=(const AAudioCallbackRegistry &) = delete;
    AAudioCallbackRegistry(AAudioCallbackRegistry &&) = delete;
    AAudioCallbackRegistry &operator=(AAudioCallbackRegistry &&) = delete;

    /**
     * Allocate a unique callback token for a newly constructed stream.
     * Called during stream construction before the stream is opened.
     */
    callback_token_t allocateCallbackToken();

    /**
     * Register an opened stream in the callback registry.
     * Called after a stream successfully opens.
     */
    void addStream(AudioStreamAAudio* stream);

    /**
     * Remove a stream from the callback registry.
     * Called during close() before the stream is destroyed.
     */
    void removeStream(AudioStreamAAudio* stream);

    /**
     * Acquire strong ownership of a stream for use inside a callback.
     *
     * This locks the weak registry reference and returns shared ownership of
     * the AAudio stream and its parent (if wrapped by FilterAudioStream).
     *
     * @param token unique callback token assigned at stream construction
     * @return tuple:
     *         - bool: true if the stream is registered and safe to use, false if the callback must abort
     *         - shared_ptr to the stream (valid if bool == true)
     *         - shared_ptr to the parent stream (valid if wrapped and bool == true)
     *
     * Failure means the stream was closed or released and the callback must exit immediately.
     */
    std::tuple<bool,
               std::shared_ptr<AudioStreamAAudio>,
               std::shared_ptr<AudioStream>>
    getStream(callback_token_t token);

private:
    // Private constructor to prevent direct instantiation
    AAudioCallbackRegistry() {
        // Reserve space in lookup table to avoid allocations in high-priority audio thread.
        mStreams.reserve(64);
    }

    std::mutex mLock;
    std::unordered_map<callback_token_t, std::weak_ptr<AudioStream>> mStreams;
    std::atomic<callback_token_t> mNextCallbackToken = kCallbackTokenUnspecified + 1;
};

} // namespace oboe

#endif // OBOE_STREAM_AAUDIO_H_
