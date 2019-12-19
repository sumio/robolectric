package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.copyOfRange;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.shadows.ShadowLooper.shadowMainLooper;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.Callback;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaFormat;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.verification.VerificationMode;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaCodec.CodecConfig;

/** Tests for {@link ShadowMediaCodec}. */
@RunWith(AndroidJUnit4.class)
@Config(minSdk = LOLLIPOP)
public final class ShadowMediaCodecTest {
  private static final String AUDIO_MIME = "audio/fake";
  private static final int WITHOUT_TIMEOUT = -1;

  private Callback callback;

  @After
  public void tearDown() throws Exception {
    ShadowMediaCodec.clearCodecs();
  }

  @Test
  public void formatChangeReported() throws IOException {
    MediaCodec codec = createAsyncEncoder();
    verify(callback).onOutputFormatChanged(same(codec), any());
  }

  @Test
  public void presentsInputBuffer() throws IOException {
    MediaCodec codec = createAsyncEncoder();
    verify(callback).onInputBufferAvailable(same(codec), anyInt());
  }

  @Test
  public void providesValidInputBuffer() throws IOException {
    MediaCodec codec = createAsyncEncoder();
    ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(callback).onInputBufferAvailable(same(codec), indexCaptor.capture());

    ByteBuffer buffer = codec.getInputBuffer(indexCaptor.getValue());

    assertThat(buffer.remaining()).isGreaterThan(0);
  }

  @Test
  public void presentsOutputBufferAfterQueuingInputBuffer() throws IOException {
    MediaCodec codec = createAsyncEncoder();
    ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(callback).onInputBufferAvailable(same(codec), indexCaptor.capture());

    ByteBuffer buffer = codec.getInputBuffer(indexCaptor.getValue());

    int start = buffer.position();
    // "Write" to the buffer.
    buffer.position(buffer.limit());

    codec.queueInputBuffer(
        indexCaptor.getValue(),
        /* offset= */ start,
        /* size= */ buffer.position() - start,
        /* presentationTimeUs= */ 0,
        /* flags= */ 0);

    asyncVerify(callback).onOutputBufferAvailable(same(codec), anyInt(), any());
  }

  @Test
  public void providesValidOutputBuffer() throws IOException {
    MediaCodec codec = createAsyncEncoder();
    ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(callback).onInputBufferAvailable(same(codec), indexCaptor.capture());

    ByteBuffer buffer = codec.getInputBuffer(indexCaptor.getValue());

    int start = buffer.position();
    // "Write" to the buffer.
    buffer.position(buffer.limit());

    codec.queueInputBuffer(
        indexCaptor.getValue(),
        /* offset= */ start,
        /* size= */ buffer.position() - start,
        /* presentationTimeUs= */ 0,
        /* flags= */ 0);

    asyncVerify(callback).onOutputBufferAvailable(same(codec), indexCaptor.capture(), any());

    buffer = codec.getOutputBuffer(indexCaptor.getValue());

    assertThat(buffer.remaining()).isGreaterThan(0);
  }

  @Test
  public void presentsInputBufferAfterReleasingOutputBufferWhenNotFinished() throws IOException {
    MediaCodec codec = createAsyncEncoder();
    ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(callback).onInputBufferAvailable(same(codec), indexCaptor.capture());

    ByteBuffer buffer = codec.getInputBuffer(indexCaptor.getValue());

    int start = buffer.position();
    // "Write" to the buffer.
    buffer.position(buffer.limit());

    codec.queueInputBuffer(
        indexCaptor.getValue(),
        /* offset= */ start,
        /* size= */ buffer.position() - start,
        /* presentationTimeUs= */ 0,
        /* flags= */ 0);

    asyncVerify(callback).onOutputBufferAvailable(same(codec), indexCaptor.capture(), any());

    codec.releaseOutputBuffer(indexCaptor.getValue(), /* render= */ false);

    asyncVerify(callback, times(2)).onInputBufferAvailable(same(codec), anyInt());
  }

  @Test
  public void doesNotPresentInputBufferAfterReleasingOutputBufferFinished() throws IOException {
    MediaCodec codec = createAsyncEncoder();
    ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(callback).onInputBufferAvailable(same(codec), indexCaptor.capture());

    ByteBuffer buffer = codec.getInputBuffer(indexCaptor.getValue());

    int start = buffer.position();
    // "Write" to the buffer.
    buffer.position(buffer.limit());

    codec.queueInputBuffer(
        indexCaptor.getValue(),
        /* offset= */ start,
        /* size= */ buffer.position() - start,
        /* presentationTimeUs= */ 0,
        /* flags= */ MediaCodec.BUFFER_FLAG_END_OF_STREAM);

    asyncVerify(callback).onOutputBufferAvailable(same(codec), indexCaptor.capture(), any());

    codec.releaseOutputBuffer(indexCaptor.getValue(), /* render= */ false);

    asyncVerify(callback, times(1)).onInputBufferAvailable(same(codec), anyInt());
  }

  @Test
  public void passesEndOfStreamFlagWithFinalOutputBuffer() throws IOException {
    MediaCodec codec = createAsyncEncoder();
    ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(callback).onInputBufferAvailable(same(codec), indexCaptor.capture());

    ByteBuffer buffer = codec.getInputBuffer(indexCaptor.getValue());

    int start = buffer.position();
    // "Write" to the buffer.
    buffer.position(buffer.limit());

    codec.queueInputBuffer(
        indexCaptor.getValue(),
        /* offset= */ start,
        /* size= */ buffer.position() - start,
        /* presentationTimeUs= */ 0,
        /* flags= */ MediaCodec.BUFFER_FLAG_END_OF_STREAM);

    ArgumentCaptor<BufferInfo> infoCaptor = ArgumentCaptor.forClass(BufferInfo.class);

    asyncVerify(callback)
        .onOutputBufferAvailable(same(codec), indexCaptor.capture(), infoCaptor.capture());

    assertThat(infoCaptor.getValue().flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM).isNotEqualTo(0);
  }

  @Test
  public void whenCustomCodec_InputBufferIsOfExpectedSize() throws Exception {
    int inputBufferSize = 1000;
    CodecConfig config = new CodecConfig(inputBufferSize, /*outputBufferSize=*/ 0, (in, out) -> {});
    ShadowMediaCodec.addEncoder(AUDIO_MIME, config);

    MediaCodec codec = createSyncEncoder();

    ByteBuffer inputBuffer = codec.getInputBuffer(codec.dequeueInputBuffer(0));
    assertThat(inputBuffer.capacity()).isEqualTo(inputBufferSize);
  }

  @Test
  public void whenCustomCodec_OutputBufferIsOfExpectedSize() throws Exception {
    int outputBufferSize = 1000;
    CodecConfig config = new CodecConfig(/*inputBufferSize=*/ 0, outputBufferSize, (in, out) -> {});
    ShadowMediaCodec.addEncoder(AUDIO_MIME, config);
    MediaCodec codec = createSyncEncoder();

    int inputBuffer = codec.dequeueInputBuffer(/*timeoutUs=*/ 0);
    codec.queueInputBuffer(
        inputBuffer, /* offset=*/ 0, /* size=*/ 0, /* presentationTimeUs=*/ 0, /* flags=*/ 0);

    assertThat(codec.dequeueOutputBuffer(new BufferInfo(), /* timeoutUs= */ 0))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);

    ByteBuffer outputBuffer =
        codec.getOutputBuffer(codec.dequeueOutputBuffer(new BufferInfo(), /*timeoutUs=*/ 0));
    assertThat(outputBuffer.capacity()).isEqualTo(outputBufferSize);
  }

  @Test
  public void inSyncMode_outputBufferInfoPopulated() throws Exception {
    MediaCodec codec = createSyncEncoder();
    int inputBuffer = codec.dequeueInputBuffer(/*timeoutUs=*/ 0);
    codec.getInputBuffer(inputBuffer).put(ByteBuffer.allocateDirect(512));
    codec.queueInputBuffer(
        inputBuffer,
        /* offset= */ 0,
        /* size= */ 512,
        /* presentationTimeUs= */ 123456,
        /* flags= */ MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    BufferInfo info = new BufferInfo();

    assertThat(codec.dequeueOutputBuffer(info, /* timeoutUs= */ 0))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);

    codec.dequeueOutputBuffer(info, /* timeoutUs= */ 0);

    assertThat(info.offset).isEqualTo(0);
    assertThat(info.size).isEqualTo(512);
    assertThat(info.flags).isEqualTo(MediaCodec.BUFFER_FLAG_END_OF_STREAM);
    assertThat(info.presentationTimeUs).isEqualTo(123456);
  }

  @Test
  public void inSyncMode_encodedDataIsCorrect() throws Exception {
    ByteBuffer src = ByteBuffer.wrap(generateByteArray(512));
    ByteBuffer dst = ByteBuffer.wrap(new byte[512]);

    MediaCodec codec = createSyncEncoder();
    process(codec, src, dst);

    src.clear();
    dst.clear();
    assertThat(dst.array()).isEqualTo(generateByteArray(512));
  }

  @Test
  public void inSyncMode_encodedDataIsCorrectForCustomCodec() throws Exception {
    ShadowMediaCodec.addEncoder(
        AUDIO_MIME,
        new CodecConfig(
            1000,
            100,
            (in, out) -> {
              ByteBuffer inClone = in.duplicate();
              inClone.limit(in.remaining() / 10);
              out.put(inClone);
            }));
    byte[] input = generateByteArray(4000);
    ByteBuffer src = ByteBuffer.wrap(input);
    ByteBuffer dst = ByteBuffer.wrap(new byte[400]);

    MediaCodec codec = createSyncEncoder();
    process(codec, src, dst);

    assertThat(Arrays.copyOf(dst.array(), 100)).isEqualTo(copyOfRange(input, 0, 100));
    assertThat(copyOfRange(dst.array(), 100, 200)).isEqualTo(copyOfRange(input, 1000, 1100));
    assertThat(copyOfRange(dst.array(), 200, 300)).isEqualTo(copyOfRange(input, 2000, 2100));
    assertThat(copyOfRange(dst.array(), 300, 400)).isEqualTo(copyOfRange(input, 3000, 3100));
  }

  @Test
  public void inSyncMode_firstOutputBufferIndexAfterConfigureIsFormatChange() throws Exception {
    MediaCodec codec = createSyncEncoder();
    assertThat(codec.dequeueOutputBuffer(new BufferInfo(), 0))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
  }

  @Test
  public void inSyncMode_outputFormatSet() throws Exception {
    MediaCodec codec = createSyncEncoder();

    MediaFormat defaultFormat = codec.getOutputFormat();
    MediaFormat mediaFormat = new MediaFormat();
    mediaFormat.setInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT, 2);
    mediaFormat.setInteger(android.media.MediaFormat.KEY_SAMPLE_RATE, 5000);
    ShadowMediaCodec.setOutputFormat(mediaFormat);

    assertThat(defaultFormat).isInstanceOf(MediaFormat.class);
    assertThat(codec.getOutputFormat()).isEqualTo(mediaFormat);
  }

  public static <T> T asyncVerify(T mock) {
    shadowMainLooper().idle();
    return verify(mock);
  }

  public static <T> T asyncVerify(T mock, VerificationMode mode) {
    shadowMainLooper().idle();
    return verify(mock, mode);
  }

  private MediaCodec createAsyncEncoder() throws IOException {
    MediaCodec codec = MediaCodec.createEncoderByType(AUDIO_MIME);
    callback = mock(MediaCodecCallback.class);
    codec.setCallback(callback);

    codec.configure(
        getBasicAACFormat(),
        /* surface= */ null,
        /* crypto= */ null,
        MediaCodec.CONFIGURE_FLAG_ENCODE);
    codec.start();

    shadowMainLooper().idle();

    return codec;
  }

  private static MediaCodec createSyncEncoder() throws IOException {
    MediaCodec codec = MediaCodec.createEncoderByType(AUDIO_MIME);
    codec.configure(
        getBasicAACFormat(),
        /* surface= */ null,
        /* crypto= */ null,
        MediaCodec.CONFIGURE_FLAG_ENCODE);
    codec.start();

    shadowMainLooper().idle();

    return codec;
  }

  private static MediaFormat getBasicAACFormat() {
    MediaFormat format = new MediaFormat();
    format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
    format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
    format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
    format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 16000);
    format.setInteger(MediaFormat.KEY_AAC_PROFILE, CodecProfileLevel.AACObjectLC);

    return format;
  }

  /** Concrete class extending MediaCodec.Callback to facilitate mocking. */
  public static class MediaCodecCallback extends MediaCodec.Callback {

    @Override
    public void onInputBufferAvailable(MediaCodec codec, int inputBufferId) {}

    @Override
    public void onOutputBufferAvailable(MediaCodec codec, int outputBufferId, BufferInfo info) {}

    @Override
    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {}

    @Override
    public void onError(MediaCodec codec, MediaCodec.CodecException e) {}
  }

  /**
   * A pure function which generates a byte[] of a given size contain values between {@link
   * Byte#MIN_VALUE} and {@link Byte#MAX_VALUE},
   */
  private static byte[] generateByteArray(int size) {
    byte[] array = new byte[size];
    for (int i = 0; i < size; i++) {
      array[i] = (byte) (i % 255 - Byte.MIN_VALUE);
    }
    return array;
  }

  /**
   * Simply moves the data in the {@code src} buffer across a given {@link MediaCodec} and stores
   * the output in {@code dst}.
   */
  private static void process(MediaCodec codec, ByteBuffer src, ByteBuffer dst) {
    while (true) {
      if (src.hasRemaining()) {
        writeToInputBuffer(codec, src);
        if (!src.hasRemaining()) {
          writeEndOfInput(codec);
        }
      }

      BufferInfo info = new BufferInfo();
      int outputBufferId = codec.dequeueOutputBuffer(info, 0);
      if (outputBufferId >= 0) {
        ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
        dst.put(outputBuffer);
        codec.releaseOutputBuffer(outputBufferId, false);
      }

      if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        break;
      }
    }
    codec.stop();
    codec.release();
  }

  /** Writes as much of {@code src} to the next available input buffer. */
  private static void writeToInputBuffer(MediaCodec codec, ByteBuffer src) {
    int inputBufferId = codec.dequeueInputBuffer(WITHOUT_TIMEOUT);
    ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
    // API versions lower than 21 don't clear the buffer before returning it.
    if (Build.VERSION.SDK_INT < 21) {
      inputBuffer.clear();
    }
    int srcLimit = src.limit();
    int numberOfBytesToWrite = Math.min(src.remaining(), inputBuffer.remaining());
    src.limit(src.position() + numberOfBytesToWrite);
    inputBuffer.put(src);
    src.limit(srcLimit);
    codec.queueInputBuffer(inputBufferId, 0, numberOfBytesToWrite, 0, 0);
  }

  /** Writes end of input to the next available input buffer */
  private static void writeEndOfInput(MediaCodec codec) {
    int inputBufferId = codec.dequeueInputBuffer(WITHOUT_TIMEOUT);
    codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
  }
}
