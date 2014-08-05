package freenet.client;

import com.db4o.ObjectContainer;

import freenet.client.ArchiveManager.ARCHIVE_TYPE;
import freenet.client.async.ClientContext;
import freenet.keys.FreenetURI;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.compress.Compressor.COMPRESSOR_TYPE;

// WARNING: THIS CLASS IS STORED IN DB4O -- THINK TWICE BEFORE ADD/REMOVE/RENAME FIELDS
class ArchiveHandlerImpl implements ArchiveHandler {

	private static volatile boolean logMINOR;

	static {
		Logger.registerClass(ArchiveHandlerImpl.class);
	}

	private final FreenetURI key;
	private boolean forceRefetchArchive;
	ARCHIVE_TYPE archiveType;
	COMPRESSOR_TYPE compressorType;

	ArchiveHandlerImpl(FreenetURI key, ARCHIVE_TYPE archiveType, COMPRESSOR_TYPE ctype, boolean forceRefetchArchive) {
		this.key = key;
		this.archiveType = archiveType;
		this.compressorType = ctype;
		this.forceRefetchArchive = forceRefetchArchive;
	}

	@Override
	public Bucket get(String internalName, ArchiveContext archiveContext,
			ArchiveManager manager, ObjectContainer container)
			throws ArchiveFailureException, ArchiveRestartException,
			MetadataParseException, FetchException {

		if(forceRefetchArchive) return null;

		Bucket data;

		// Fetch from cache
		if(logMINOR)
			Logger.minor(this, "Checking cache: "+key+ ' ' +internalName);
		if((data = manager.getCached(key, internalName)) != null) {
			return data;
		}

		return null;
	}

	@Override
	public Bucket getMetadata(ArchiveContext archiveContext,
			ArchiveManager manager, ObjectContainer container) throws ArchiveFailureException,
			ArchiveRestartException, MetadataParseException, FetchException {
		return get(".metadata", archiveContext, manager, container);
	}

	@Override
	public void extractToCache(Bucket bucket, ArchiveContext actx,
			String element, ArchiveExtractCallback callback,
			ArchiveManager manager, ObjectContainer container, ClientContext context) throws ArchiveFailureException,
			ArchiveRestartException {
		forceRefetchArchive = false; // now we don't need to force refetch any more
		ArchiveStoreContext ctx = manager.makeContext(key, archiveType, compressorType, false);
		manager.extractToCache(key, archiveType, compressorType, bucket, actx, ctx, element, callback, container, context);
	}

	@Override
	public ARCHIVE_TYPE getArchiveType() {
		return archiveType;
	}

	public COMPRESSOR_TYPE getCompressorType() {
		return compressorType;
	}

	@Override
	public FreenetURI getKey() {
		return key;
	}

	private static class ProxyCallback implements ArchiveExtractCallback {

		Bucket data;

		@Override
		public void gotBucket(Bucket data, ClientContext context) {
			this.data = data;
		}

		@Override
		public void notInArchive(ClientContext context) {
			this.data = null;
		}

		@Override
		public void onFailed(ArchiveRestartException e, ClientContext context) {
			// Must not be called.
			throw new UnsupportedOperationException();
		}

		@Override
		public void onFailed(ArchiveFailureException e, ClientContext context) {
			// Must not be called.
			throw new UnsupportedOperationException();
		}

	}

	@Override
	public void activateForExecution(ObjectContainer container) {
		container.activate(this, 1);
		container.activate(key, 5);
	}

	@Override
	public ArchiveHandler cloneHandler() {
		return new ArchiveHandlerImpl(key.clone(), archiveType, compressorType, forceRefetchArchive);
	}

	@Override
	public void removeFrom(ObjectContainer container) {
		if(key == null) {
			Logger.error(this, "removeFrom() : key = null for "+this+" I exist = "+container.ext().isStored(this)+" I am active: "+container.ext().isActive(this), new Exception("error"));
		} else
			key.removeFrom(container);
		container.delete(this);
	}

}
