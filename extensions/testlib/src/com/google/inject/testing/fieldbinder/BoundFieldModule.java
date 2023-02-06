@Inject @Toolable void initialize(Injector injector) {if (this.injector != null) {throw new ConfigurationException( ImmutableList.of(new Message(FactoryProvider2.class,"Factories.create() factories may only be used in one Injector!")));

}this.injector = injector;for (Map.Entry<Method, AssistData> entry : assistDataByMethod.entrySet()) { Method method = entry.getKey();  AssistData data = entry.getValue(); Object[] args;

  if (!data.optimized) { args = new Object[method.getParameterTypes().length]; Arrays.fill(args, "dummy object for validating Factories"); } else {  args = null; // won't be used -- instead will bind to data.providers.

  } getBindingFromNewInjector( method, args, data); // throws if the binding isn't properly configured}} public Binding<?> getBindingFromNewInjector(final Method method, final Object[] args, final AssistData data) { checkState(injector != null, "Factories.create() factories cannot be used until they're initialized by Guice.");

final Key<?> returnType = data.returnType;// We ignore any pre-existing binding annotation.final Key<?> returnKey = Key.get(returnType.getTypeLiteral(), RETURN_ANNOTATION); Module assistedModule =

new AbstractModule() { @Override @SuppressWarnings({ "unchecked","rawtypes"}) // raw keys are necessary for the args array and return value protected void configure() { Binder binder = binder().withSource(method);

int p = 0;if (!data.optimized) {for (Key<?> paramKey : data.paramTypes) {// Wrap in a Provider to cover null, and to prevent Guice from injecting the // parameter binder.bind((Key) paramKey).toProvider(Providers.of(args[p++]));}} else {

for (Key<?> paramKey : data.paramTypes) {// Bind to our ThreadLocalProviders.binder.bind((Key) paramKey).toProvider(data.providers.get(p++));}} Constructor constructor = data.constructor;

// Constructor *should* always be non-null here,// but if it isn't, we'll end up throwing a fairly good error// message for the user. 

if (constructor != null) {binder.bind(returnKey).toConstructor(constructor, (TypeLiteral) data.implementationType) .in(Scopes.NO_SCOPE); // make sure we erase any scope on the implementation type }}};

Injector forCreate = injector.createChildInjector(assistedModule); Binding<?> binding = forCreate.getBinding(returnKey);if (data.optimized) {data.cachedBinding = binding;}return binding;}@Override public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable { if (methodHandleByMethod.containsKey(method)) {return methodHandleByMethod.get(method).invokeWithArguments(args);}if (method.getDeclaringClass().equals(Object.class)) {if ("equals".equals(method.getName())) {return proxy == args[0];} else if ("hashCode".equals(method.getName())) { return System.identityHashCode(proxy);} else {return method.invoke(this, args);}}

AssistData data = assistDataByMethod.get(method);checkState(data != null, "No data for method: %s", method);Provider<?> provider; if (data.cachedBinding != null) {provider = data.cachedBinding.getProvider();

} else {provider = getBindingFromNewInjector(method, args, data).getProvider();}try {int p = 0;for (ThreadLocalProvider tlp : data.providers) { tlp.set(args[p++]); }return provider.get();

} catch (ProvisionException e) {if (e.getErrorMessages().size() == 1) {Message onlyError = getOnlyElement(e.getErrorMessages());Throwable cause = onlyError.getCause();if (cause != null && canRethrow(method, cause)) {

      throw cause; }}throw e;} finally {for (ThreadLocalProvider tlp : data.providers) { tlp.remove(); } } }
